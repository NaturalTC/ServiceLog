package dev.servicelog.auth;

// Thrown for any failed login. Deliberately says the same thing whether the email
// doesn't exist or the password is wrong, so the API never reveals which accounts exist.
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
