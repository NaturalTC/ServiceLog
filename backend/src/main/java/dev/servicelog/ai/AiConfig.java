package dev.servicelog.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

// AI drafts are optional: without OPENAI_API_KEY the feature is simply off, and the app
// works exactly as before with the manual form.
@Configuration
public class AiConfig {

    @Bean
    public NoteExtractor noteExtractor(@Value("${servicelog.ai.openai-api-key:}") String apiKey,
                                       @Value("${servicelog.ai.model}") String model,
                                       @Value("${servicelog.ai.base-url}") String baseUrl,
                                       @Value("${servicelog.ai.timeout-seconds}") int timeoutSeconds,
                                       JsonMapper jsonMapper) {
        if (apiKey.isBlank()) {
            return (note, type) -> {
                throw new AiUnavailableException("AI drafts aren't set up on this server. Fill in the form by hand.");
            };
        }
        return new OpenAiNoteExtractor(openAi(apiKey, baseUrl, timeoutSeconds), jsonMapper, model);
    }

    // For the assistant (S33) and form filling: any chat request, with tools if needed
    @Bean
    public ChatModel chatModel(@Value("${servicelog.ai.openai-api-key:}") String apiKey,
                               @Value("${servicelog.ai.model}") String model,
                               @Value("${servicelog.ai.base-url}") String baseUrl,
                               @Value("${servicelog.ai.assistant-timeout-seconds:60}") int timeoutSeconds,
                               @Value("${servicelog.ai.audit-file:}") String auditFile,
                               JsonMapper jsonMapper) {
        if (apiKey.isBlank()) {
            return request -> {
                throw new AiUnavailableException("The AI assistant isn't set up on this server.");
            };
        }
        ChatModel openAi = new OpenAiChatModel(openAi(apiKey, baseUrl, timeoutSeconds), model);
        return auditFile.isBlank() ? openAi : new AuditedChatModel(openAi, Path.of(auditFile), jsonMapper);
    }

    private static RestClient openAi(String apiKey, String baseUrl, int timeoutSeconds) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }
}
