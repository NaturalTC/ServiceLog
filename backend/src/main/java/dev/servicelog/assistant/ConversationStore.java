package dev.servicelog.assistant;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Open chats, in memory: they're short-lived, a restart simply starts fresh, and nothing
// with placeholders-to-real-names mappings is written to disk.
@Component
class ConversationStore {

    private static final Duration IDLE_LIMIT = Duration.ofHours(2);
    private static final int MAX_OPEN = 50;

    private final Map<String, Conversation> open = new ConcurrentHashMap<>();
    private final Clock clock;

    ConversationStore(Clock clock) {
        this.clock = clock;
    }

    // The chat with this id if it's still open, otherwise a new one
    Conversation getOrStart(String id) {
        Instant now = clock.instant();
        open.values().removeIf(c -> c.lastUsed.isBefore(now.minus(IDLE_LIMIT)));
        Conversation existing = id == null ? null : open.get(id);
        if (existing != null) {
            existing.lastUsed = now;
            return existing;
        }
        while (open.size() >= MAX_OPEN) {
            open.values().stream().min(Comparator.comparing((Conversation c) -> c.lastUsed))
                    .ifPresent(oldest -> open.remove(oldest.id));
        }
        Conversation started = new Conversation(UUID.randomUUID().toString(), now);
        open.put(started.id, started);
        return started;
    }

    void end(String id) {
        open.remove(id);
    }
}
