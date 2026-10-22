package dev.servicelog.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Slows down password guessing: after too many failed logins for one email, or from one
// network address, within a time window, further attempts are refused until it passes.
//
// Kept in memory, which suits a single server. If ServiceLog ever runs on several
// servers, this moves to a shared store such as Redis.
@Component
public class LoginAttemptLimiter {

    // Stop tracking new keys past this many, so a flood of random emails can't eat memory
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(Clock clock,
                               @Value("${app.login.max-failures:5}") int maxFailures,
                               @Value("${app.login.window-minutes:15}") long windowMinutes) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    // How long until this email and address may try again; empty if they may try now
    public Optional<Duration> retryAfter(String email, String clientAddress) {
        Instant now = Instant.now(clock);
        Duration wait = Duration.ZERO;
        for (String key : keys(email, clientAddress)) {
            Deque<Instant> attempts = failures.get(key);
            if (attempts == null) {
                continue;
            }
            synchronized (attempts) {
                prune(attempts, now);
                if (attempts.size() >= maxFailures) {
                    // Blocked until the oldest failure in the window expires
                    Duration until = Duration.between(now, attempts.peekFirst().plus(window));
                    if (until.compareTo(wait) > 0) {
                        wait = until;
                    }
                }
            }
        }
        return wait.isZero() ? Optional.empty() : Optional.of(wait);
    }

    public void recordFailure(String email, String clientAddress) {
        Instant now = Instant.now(clock);
        for (String key : keys(email, clientAddress)) {
            if (!failures.containsKey(key) && failures.size() >= MAX_TRACKED_KEYS) {
                failures.entrySet().removeIf(entry -> {
                    synchronized (entry.getValue()) {
                        prune(entry.getValue(), now);
                        return entry.getValue().isEmpty();
                    }
                });
            }
            Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (attempts) {
                prune(attempts, now);
                attempts.addLast(now);
            }
        }
    }

    // A successful login clears the email's count (not the address's: one person's
    // success shouldn't unlock guessing at other accounts from the same address)
    public void recordSuccess(String email) {
        failures.remove(emailKey(email));
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }

    private static String[] keys(String email, String clientAddress) {
        return new String[] {emailKey(email), "ip:" + clientAddress};
    }

    private static String emailKey(String email) {
        return "email:" + email.trim().toLowerCase(Locale.ROOT);
    }
}
