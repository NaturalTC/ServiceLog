package dev.servicelog.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Request and response bodies for /api/auth
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    // The frontend keeps the token and sends it as "Authorization: Bearer <token>"
    public record LoginResponse(String token, long expiresInSeconds) {
    }

    public record CurrentUser(String email) {
    }
}
