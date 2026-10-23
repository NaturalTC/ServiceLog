package dev.servicelog.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditedChatModelTest {

    @Test
    void writesEachRequestAsALine_withoutImages(@TempDir Path dir) throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        Path file = dir.resolve("audit.jsonl");
        ChatModel model = new AuditedChatModel(request -> json.readTree("{\"ok\": true}"), file, json);

        model.complete(Map.of("messages", List.of(Map.of("role", "user", "content", "What's on for Customer_3?"))));
        model.complete(Map.of("messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64,/9j/4AAQSkZJRg==")))))));

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("Customer_3");
        assertThat(lines.get(1)).contains("[image]").doesNotContain("base64");
    }
}
