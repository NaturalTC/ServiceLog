package dev.servicelog.assistant;

import dev.servicelog.ai.AiUnavailableException;
import dev.servicelog.ai.AiUsageLimiter;
import dev.servicelog.ai.ChatModel;
import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.photo.ImageProcessor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// "Read nameplate" (S36): a photo of a unit's data plate in, brand, model and serial
// number out, for the owner to check on the equipment form. Nothing is saved.
//
// The photo is re-encoded first (as for service record photos), which drops hidden
// metadata such as the GPS location before it goes to the AI provider. A plate the AI
// can't read clearly comes back empty rather than guessed.
@Service
public class NameplateReader {

    // Enough to read small print on a plate; keeps the upload and the cost down
    private static final int MAX_SIZE = 1600;
    private static final Pattern PART_NUMBER = Pattern.compile("[A-Z0-9][A-Z0-9 ./_-]{1,49}");

    static final String INSTRUCTIONS = """
            You read the nameplate (data plate / rating plate) of HVAC equipment from a photo taken by a
            technician, often at an angle, in a dim basement, with glare or dirt.

            Return:
            - readable: false if this isn't a nameplate or you can't make out the model or serial at all.
            - brand: the manufacturer as usually written (Weil-McLain, Mitsubishi Electric -> Mitsubishi,
              Carrier, Trane, Rheem, Navien, Beckett, Burnham...).
            - model and serial_number exactly as printed: the values after labels like "Model", "Model No.",
              "M/N" and "Serial", "Serial No.", "S/N". Never mix them up and never complete a value you can't
              fully read: if any character is unclear, return null for that field.
            - type: the kind of equipment if the plate or the photo makes it clear, else null.
            - manufactured: the manufacture date if printed (as YYYY-MM or YYYY-MM-DD), else null. Don't
              decode it from the serial number.
            - details: up to three short facts useful on a service call (refrigerant and charge, voltage,
              input BTU/h, nozzle size), as printed; empty if none.
            - confidence: high when every returned value is crisp, medium when the photo is rough but the
              values are clear, low otherwise.
            """;

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object", "additionalProperties", false,
            "required", List.of("readable", "brand", "model", "serial_number", "type", "manufactured", "details",
                    "confidence"),
            "properties", Map.of(
                    "readable", Map.of("type", "boolean"),
                    "brand", Map.of("type", List.of("string", "null")),
                    "model", Map.of("type", List.of("string", "null")),
                    "serial_number", Map.of("type", List.of("string", "null")),
                    "type", Map.of("type", List.of("string", "null"), "enum", equipmentTypes()),
                    "manufactured", Map.of("type", List.of("string", "null")),
                    "details", Map.of("type", "array", "items", Map.of("type", "string")),
                    "confidence", Map.of("type", "string", "enum", List.of("high", "medium", "low"))));

    private final ChatModel model;
    private final AiUsageLimiter limiter;
    private final JsonMapper json;

    public NameplateReader(ChatModel model, AiUsageLimiter limiter, JsonMapper json) {
        this.model = model;
        this.limiter = limiter;
        this.json = json;
    }

    public Map<String, Object> read(byte[] photo) {
        String image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(ImageProcessor.forAi(photo, MAX_SIZE));
        limiter.acquire();
        JsonNode message = model.complete(Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", INSTRUCTIONS),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", "Read this nameplate."),
                                Map.of("type", "image_url", "image_url", Map.of("url", image, "detail", "high"))))),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "nameplate", "strict", true, "schema", SCHEMA))))
                .path("choices").path(0).path("message");
        if (message.path("refusal").isString()) {
            throw new AiUnavailableException("The AI couldn't read this photo. Fill in the form by hand.");
        }
        try {
            return toFields(json.readTree(message.path("content").asString()));
        } catch (RuntimeException e) {
            throw new AiUnavailableException("The AI's answer couldn't be used. Fill in the form by hand.", e);
        }
    }

    // Checks and cleans every value; anything that doesn't look like a model or serial is dropped
    static Map<String, Object> toFields(JsonNode a) {
        boolean readable = a.path("readable").asBoolean(false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("readable", readable);
        out.put("brand", readable ? text(a, "brand") : null);
        out.put("model", readable ? partNumber(text(a, "model")) : null);
        out.put("serialNumber", readable ? partNumber(text(a, "serial_number")) : null);
        String type = text(a, "type");
        out.put("type", readable && type != null
                && Arrays.stream(EquipmentType.values()).anyMatch(t -> t.name().equals(type)) ? type : null);
        String made = text(a, "manufactured");
        out.put("manufactured", readable && made != null && made.matches("\\d{4}-\\d{2}(-\\d{2})?") ? made : null);
        List<String> details = new ArrayList<>();
        if (readable) {
            for (JsonNode d : a.path("details")) {
                if (d.isString() && !d.asString().isBlank() && details.size() < 3) {
                    details.add(d.asString().trim());
                }
            }
        }
        out.put("details", details);
        String confidence = text(a, "confidence");
        out.put("confidence", readable && confidence != null ? confidence : "low");
        return out;
    }

    private static String partNumber(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().toUpperCase(Locale.ROOT);
        return PART_NUMBER.matcher(cleaned).matches() ? cleaned : null;
    }

    private static String text(JsonNode a, String field) {
        JsonNode v = a.path(field);
        return v.isString() && !v.asString().isBlank() ? v.asString().trim() : null;
    }

    private static List<String> equipmentTypes() {
        List<String> types = new ArrayList<>(Arrays.stream(EquipmentType.values()).map(Enum::name).toList());
        types.add(null);
        return types;
    }
}
