package dev.servicelog.ai;

// The AI couldn't produce a draft (not configured, over the daily limit, timed out or
// answered badly). Answered with 503; the manual form still works.
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
