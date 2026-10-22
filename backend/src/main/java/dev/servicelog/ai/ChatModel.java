package dev.servicelog.ai;

import tools.jackson.databind.JsonNode;

import java.util.Map;

// One call to a chat model (OpenAI's Chat Completions format): the request goes in, the
// response comes back. An interface so tests can script the model's answers.
public interface ChatModel {

    JsonNode complete(Map<String, Object> request);
}
