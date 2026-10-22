package dev.servicelog.ai;

import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.servicerecord.ServiceKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// The OpenAI client against a fake server: what it sends, and how it handles every kind of answer
class OpenAiNoteExtractorTest {

    private MockRestServiceServer server;
    private OpenAiNoteExtractor extractor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.example.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        extractor = new OpenAiNoteExtractor(builder.build(), JsonMapper.builder().build(), "test-model");
    }

    @Test
    void sendsTheModelPromptNoteAndStrictSchema() {
        server.expect(requestTo("https://api.example.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("Equipment: Oil burner\nNote: changed the nozzle"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andRespond(withSuccess(answer("""
                        {"kind":"TUNE_UP","workDone":"Changed the nozzle.","partsUsed":["Nozzle"],
                         "readings":[],"followUpNeeded":false,"followUpNote":null}"""), MediaType.APPLICATION_JSON));

        NoteExtraction result = extractor.extract("changed the nozzle", EquipmentType.OIL_BURNER);

        assertEquals(ServiceKind.TUNE_UP, result.kind());
        assertEquals(List.of("Nozzle"), result.partsUsed());
        server.verify();
    }

    @Test
    void parsesReadingsAndFollowUp() {
        server.expect(requestTo("https://api.example.test/v1/chat/completions"))
                .andRespond(withSuccess(answer("""
                        {"kind":"REPAIR","workDone":"Replaced igniter.","partsUsed":["Igniter"],
                         "readings":[{"name":"CO","value":12,"unit":"ppm"}],
                         "followUpNeeded":true,"followUpNote":"Quote a new burner"}"""), MediaType.APPLICATION_JSON));

        NoteExtraction result = extractor.extract("note", EquipmentType.OIL_BURNER);

        assertEquals(new NoteExtraction.Reading("CO", 12, "ppm"), result.readings().get(0));
        assertTrue(result.followUpNeeded());
        assertEquals("Quote a new burner", result.followUpNote());
    }

    @Test
    void cleansAnUntrustworthyAnswer() {
        server.expect(requestTo("https://api.example.test/v1/chat/completions"))
                .andRespond(withSuccess(answer("""
                        {"kind":"SPACESHIP","workDone":"  Did work.  ","partsUsed":["  Filter  ",""," "],
                         "readings":[{"name":"CO","value":"lots","unit":"ppm"},{"name":"","value":3,"unit":""}],
                         "followUpNeeded":false,"followUpNote":"stale text"}"""), MediaType.APPLICATION_JSON));

        NoteExtraction result = extractor.extract("note", EquipmentType.FURNACE);

        assertEquals(ServiceKind.OTHER, result.kind());
        assertEquals("Did work.", result.workDone());
        assertEquals(List.of("Filter"), result.partsUsed());
        assertTrue(result.readings().isEmpty());
        assertFalse(result.followUpNeeded());
        assertNull(result.followUpNote());
    }

    @Test
    void refusal_isUnavailable() {
        server.expect(requestTo("https://api.example.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":null,"refusal":"I can't help."}}]}""",
                        MediaType.APPLICATION_JSON));

        assertThrows(AiUnavailableException.class, () -> extractor.extract("note", EquipmentType.FURNACE));
    }

    @Test
    void providerError_isUnavailable() {
        server.expect(requestTo("https://api.example.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(AiUnavailableException.class, () -> extractor.extract("note", EquipmentType.FURNACE));
    }

    @Test
    void garbledContent_isUnavailable() {
        server.expect(requestTo("https://api.example.test/v1/chat/completions"))
                .andRespond(withSuccess(answer("not json at all"), MediaType.APPLICATION_JSON));

        assertThrows(AiUnavailableException.class, () -> extractor.extract("note", EquipmentType.FURNACE));
    }

    @Test
    void promptContainsNoTestSetNotes() {
        // Copying evaluation notes into the prompt would make the accuracy score meaningless
        for (String fromTestSet : List.of("beckett", "taco 007", "bulging", "emery cloth", "Navien")) {
            assertFalse(OpenAiNoteExtractor.SYSTEM_PROMPT.toLowerCase().contains(fromTestSet.toLowerCase()), fromTestSet);
        }
    }

    // A Chat Completions response whose message content is the given JSON text
    private static String answer(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return """
                {"choices":[{"message":{"role":"assistant","content":"%s"}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5}}""".formatted(escaped);
    }
}
