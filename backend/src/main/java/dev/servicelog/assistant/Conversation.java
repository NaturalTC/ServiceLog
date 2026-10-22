package dev.servicelog.assistant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// One chat with the assistant: its placeholders and the messages as the AI saw them
// (with placeholders, never real details). Kept in memory for a couple of hours.
final class Conversation {

    final String id;
    final Pseudonyms pseudonyms = new Pseudonyms();
    final List<Map<String, Object>> messages = new ArrayList<>();
    Instant lastUsed;

    Conversation(String id, Instant now) {
        this.id = id;
        this.lastUsed = now;
    }
}
