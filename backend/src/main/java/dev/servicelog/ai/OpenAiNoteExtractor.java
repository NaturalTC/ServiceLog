package dev.servicelog.ai;

import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.servicerecord.ServiceKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Extracts a draft service record with OpenAI's Chat Completions API, using "structured
// outputs" (a strict JSON schema) so the answer always has the expected shape.
//
// The answer is still treated as untrusted input: every field is checked and cleaned,
// and anything unusable makes the whole draft fail rather than save something wrong.
public class OpenAiNoteExtractor implements NoteExtractor {

    private static final Logger log = LoggerFactory.getLogger(OpenAiNoteExtractor.class);

    // The instructions. No example here comes from the evaluation set (ai-eval/notes.jsonl):
    // copying test notes into the prompt would make the accuracy score meaningless.
    static final String SYSTEM_PROMPT = """
            You turn an HVAC technician's quick service note into a draft service record.
            The note may be dictated or typed in a hurry: shorthand, missing punctuation, filler words.

            Fill in:
            - kind: TUNE_UP (routine annual or seasonal service, start-up or cleaning), REPAIR (fixing a
              problem or a no-heat / no-cooling / leak call), INSTALLATION (new equipment put in),
              INSPECTION (looked at it or tested it, no service work), or OTHER.
            - workDone: one or two plain sentences summarizing what was done. Keep the technician's
              specifics (component names, what was cleaned or adjusted). No readings here.
            - partsUsed: only parts and materials actually installed, replaced or added on this visit,
              e.g. "Nozzle 0.65 70B", "Hot surface igniter", "2 lb R-410A". Leave out anything only
              cleaned, tested, inspected, recommended, ordered, brought but not used, or supplied by the
              customer. Never invent parts. An empty list is a normal answer.
              On an installation, the new equipment itself (the furnace, condenser, water heater...) is
              not a part, since it gets its own equipment record; list the materials and accessories put
              in with it (brackets, pads, line sets, line hide, disconnects, valves, pumps, fittings).
              Technicians often write just a list after a routine service ("nozzle filter strainer"):
              those are parts that were replaced.
            - readings: measurements the technician recorded, with a numeric value and a unit where one
              is given or obvious, e.g. {"name": "CO", "value": 9, "unit": "ppm"},
              {"name": "Static pressure", "value": 0.6, "unit": "in wc"}. Model numbers, sizes, ratings
              and part numbers are not readings.
            - followUpNeeded: true if the business has something left to do or offer later: a return
              visit, a part on order, a quote to send, a repair or replacement to recommend, something to
              recheck. Not things the customer will handle themselves (buying their own filter, calling
              another trade). followUpNote says what, briefly; null when no follow-up is needed.

            Use only what the note says. Words like [customer], [phone] and [email] are placeholders
            for removed private details; ignore them.
            """;

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("kind", "workDone", "partsUsed", "readings", "followUpNeeded", "followUpNote"),
            "properties", Map.of(
                    "kind", Map.of("type", "string",
                            "enum", List.of("TUNE_UP", "REPAIR", "INSTALLATION", "INSPECTION", "OTHER")),
                    "workDone", Map.of("type", "string"),
                    "partsUsed", Map.of("type", "array", "items", Map.of("type", "string")),
                    "readings", Map.of("type", "array", "items", Map.of(
                            "type", "object",
                            "additionalProperties", false,
                            "required", List.of("name", "value", "unit"),
                            "properties", Map.of(
                                    "name", Map.of("type", "string"),
                                    "value", Map.of("type", "number"),
                                    "unit", Map.of("type", "string")))),
                    "followUpNeeded", Map.of("type", "boolean"),
                    "followUpNote", Map.of("type", List.of("string", "null"))));

    private final RestClient http;
    private final JsonMapper json;
    private final String model;

    public OpenAiNoteExtractor(RestClient http, JsonMapper json, String model) {
        this.http = http;
        this.json = json;
        this.model = model;
    }

    @Override
    public NoteExtraction extract(String note, EquipmentType equipmentType) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content",
                                "Equipment: " + equipmentType.getLabel() + "\nNote: " + note)),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "service_record", "strict", true, "schema", SCHEMA)));
        JsonNode response;
        try {
            response = http.post().uri("/chat/completions").body(request).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiUnavailableException("The AI service didn't answer. Fill in the form by hand.", e);
        }
        if (response == null) {
            throw new AiUnavailableException("The AI service returned nothing. Fill in the form by hand.");
        }
        logUsage(response.path("usage"));

        JsonNode message = response.path("choices").path(0).path("message");
        if (!message.path("refusal").isMissingNode() && !message.path("refusal").isNull()) {
            throw new AiUnavailableException("The AI declined this note. Fill in the form by hand.");
        }
        try {
            return toExtraction(json.readTree(message.path("content").asString()));
        } catch (RuntimeException e) {
            throw new AiUnavailableException("The AI's answer couldn't be used. Fill in the form by hand.", e);
        }
    }

    // Validates and cleans every field of the model's answer
    static NoteExtraction toExtraction(JsonNode answer) {
        ServiceKind kind;
        try {
            kind = ServiceKind.valueOf(answer.path("kind").asString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            kind = ServiceKind.OTHER;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode part : answer.path("partsUsed")) {
            String text = part.asString().trim();
            if (!text.isEmpty() && text.length() <= 200) {
                parts.add(text);
            }
        }
        List<NoteExtraction.Reading> readings = new ArrayList<>();
        for (JsonNode r : answer.path("readings")) {
            if (r.path("value").isNumber() && !r.path("name").asString().isBlank()) {
                readings.add(new NoteExtraction.Reading(r.path("name").asString().trim(), r.path("value").asDouble(),
                        r.path("unit").asString().trim()));
            }
        }
        boolean followUp = answer.path("followUpNeeded").asBoolean(false);
        String followUpNote = answer.path("followUpNote").isString() ? answer.path("followUpNote").asString().trim()
                : null;
        return new NoteExtraction(kind, answer.path("workDone").asString().trim(), parts, readings, followUp,
                followUp && followUpNote != null && !followUpNote.isEmpty() ? followUpNote : null);
    }

    private void logUsage(JsonNode usage) {
        if (!usage.isMissingNode()) {
            log.info("AI draft: model={} prompt_tokens={} completion_tokens={}", model,
                    usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt());
        }
    }
}
