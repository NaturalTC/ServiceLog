package dev.servicelog.auth;

import java.time.Duration;

// Answered with 429 and a Retry-After header. Same wording whether or not the email
// has an account, so it can't be used to discover accounts.
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many login attempts. Try again in " + minutes(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    private static String minutes(Duration wait) {
        long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
