package dev.servicelog.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

// Writes every request sent to the AI provider to a file, one JSON object per line, so
// the assistant evaluation can prove what actually left the server contains no customer
// details (ai-eval/assistant). Off unless servicelog.ai.audit-file is set; for evaluation
// runs on demo data, not production. Images are left out, to keep the file small.
public class AuditedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final Path file;
    private final JsonMapper json;

    public AuditedChatModel(ChatModel delegate, Path file, JsonMapper json) {
        this.delegate = delegate;
        this.file = file;
        this.json = json;
    }

    @Override
    public JsonNode complete(Map<String, Object> request) {
        String line = json.writeValueAsString(request).replaceAll("data:image/[a-z]+;base64,[A-Za-z0-9+/=]+", "[image]");
        try {
            synchronized (this) {
                Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't write the AI audit file", e);
        }
        return delegate.complete(request);
    }
}
