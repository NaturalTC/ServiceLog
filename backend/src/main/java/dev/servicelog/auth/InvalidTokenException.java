package dev.servicelog.auth;

// The link is wrong, already used or expired. One message for all three.
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("This link is invalid or has expired. Request a new one.");
    }
}
