package dev.servicelog.exception;

import dev.servicelog.ai.AiUnavailableException;
import dev.servicelog.auth.EmailNotVerifiedException;
import dev.servicelog.auth.InvalidCredentialsException;
import dev.servicelog.auth.InvalidTokenException;
import dev.servicelog.auth.TooManyLoginAttemptsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

// Turns exceptions into consistent JSON errors: { status, code, message, timestamp }
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttempts(TooManyLoginAttemptsException ex) {
        ResponseEntity<Map<String, Object>> response = build(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                ex.getMessage());
        return ResponseEntity.status(response.getStatusCode())
                // Rounded up, so a client never retries a moment too early
                .header("Retry-After", String.valueOf((ex.getRetryAfter().toMillis() + 999) / 1000))
                .body(response.getBody());
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return build(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    // Backstop for the database's own constraints (e.g. two saves of the same serial number
    // at the same moment, which the service-level check can't see)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", "This change conflicts with data that already exists.");
    }

    // AI not set up, over the daily limit, or the provider didn't answer: the app works without it
    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAiUnavailable(AiUnavailableException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", ex.getMessage());
    }

    // Photos bigger than spring.servlet.multipart.max-file-size (the app shrinks them first)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "TOO_LARGE", "That upload is too large");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", "Choose at least one photo");
    }

    // Malformed JSON or wrong types (e.g. text where a number belongs)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is not valid JSON for this endpoint");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", message.isEmpty() ? "Validation failed" : message);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "code", code,
                "message", message,
                "timestamp", Instant.now().toString()));
    }
}
