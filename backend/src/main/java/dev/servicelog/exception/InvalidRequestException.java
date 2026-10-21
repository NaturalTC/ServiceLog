package dev.servicelog.exception;

// A request that passed field validation but breaks a business rule (answered with 400)
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
