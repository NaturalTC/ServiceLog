package dev.servicelog.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
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
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        RestClient http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        return new OpenAiNoteExtractor(http, jsonMapper, model);
    }
}
