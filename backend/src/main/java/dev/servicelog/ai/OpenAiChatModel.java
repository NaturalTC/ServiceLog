package dev.servicelog.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

// Sends a request to OpenAI's Chat Completions API. The model is filled in here, so
// callers only describe the conversation, tools and response format.
public class OpenAiChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatModel.class);

    private final RestClient http;
    private final String model;

    public OpenAiChatModel(RestClient http, String model) {
        this.http = http;
        this.model = model;
    }

    @Override
    public JsonNode complete(Map<String, Object> request) {
        Map<String, Object> body = new HashMap<>(request);
        body.put("model", model);
        JsonNode response;
        try {
            response = http.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiUnavailableException("The AI service didn't answer. Try again in a moment.", e);
        }
        if (response == null) {
            throw new AiUnavailableException("The AI service returned nothing. Try again in a moment.");
        }
        JsonNode usage = response.path("usage");
        if (!usage.isMissingNode()) {
            log.info("AI call: model={} prompt_tokens={} completion_tokens={}", model,
                    usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt());
        }
        return response;
    }
}
