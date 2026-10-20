package dev.servicelog.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Request and response bodies for /api/auth
public final class AuthDtos {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    // The frontend keeps the token and sends it as "Authorization: Bearer <token>"
    public record LoginResponse(String token, long expiresInSeconds) {
    }

    public record CurrentUser(String email) {
    }

    public record EmailRequest(@NotBlank @Email String email) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = MIN_PASSWORD_LENGTH, max = 200,
                    message = "must be at least " + MIN_PASSWORD_LENGTH + " characters") String newPassword) {
    }

    public record MessageResponse(String message) {
    }
}
