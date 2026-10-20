package dev.servicelog.auth;

// Thrown only after a correct password, so it doesn't reveal anything to a stranger
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Verify your email before logging in. Check your inbox for the link.");
    }
}
