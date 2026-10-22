package dev.servicelog.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-22T12:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, 15);

    @Test
    void fifthFailure_blocksTheEmail_untilTheWindowPasses() {
        failTimes(5, "owner@example.com", "1.1.1.1");

        assertEquals(Duration.ofMinutes(15), limiter.retryAfter("owner@example.com", "2.2.2.2").orElseThrow());

        clock.advance(Duration.ofMinutes(14));
        assertTrue(limiter.retryAfter("owner@example.com", "2.2.2.2").isPresent());

        clock.advance(Duration.ofMinutes(1));
        assertTrue(limiter.retryAfter("owner@example.com", "2.2.2.2").isEmpty());
    }

    @Test
    void fourFailures_areStillAllowed() {
        failTimes(4, "owner@example.com", "1.1.1.1");

        assertTrue(limiter.retryAfter("owner@example.com", "1.1.1.1").isEmpty());
    }

    @Test
    void oneAddressGuessingManyEmails_isBlocked() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("guess" + i + "@example.com", "6.6.6.6");
        }

        assertTrue(limiter.retryAfter("another@example.com", "6.6.6.6").isPresent());
        assertTrue(limiter.retryAfter("another@example.com", "7.7.7.7").isEmpty());
    }

    @Test
    void emailsAreMatchedIgnoringCaseAndSpaces() {
        failTimes(5, " Owner@Example.com", "1.1.1.1");

        assertTrue(limiter.retryAfter("owner@example.com", "9.9.9.9").isPresent());
    }

    @Test
    void success_resetsTheEmailCount() {
        failTimes(4, "owner@example.com", "1.1.1.1");
        limiter.recordSuccess("owner@example.com");
        failTimes(1, "owner@example.com", "2.2.2.2");

        assertTrue(limiter.retryAfter("owner@example.com", "3.3.3.3").isEmpty());
    }

    @Test
    void oldFailures_slideOutOfTheWindow() {
        failTimes(3, "owner@example.com", "1.1.1.1");
        clock.advance(Duration.ofMinutes(16));
        failTimes(2, "owner@example.com", "1.1.1.1");

        assertTrue(limiter.retryAfter("owner@example.com", "1.1.1.1").isEmpty());
    }

    private void failTimes(int times, String email, String address) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email, address);
        }
    }

    // A clock the test can move forward
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
