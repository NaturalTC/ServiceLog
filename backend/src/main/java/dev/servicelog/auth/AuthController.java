package dev.servicelog.auth;

import dev.servicelog.auth.AuthDtos.CurrentUser;
import dev.servicelog.auth.AuthDtos.EmailRequest;
import dev.servicelog.auth.AuthDtos.LoginRequest;
import dev.servicelog.auth.AuthDtos.LoginResponse;
import dev.servicelog.auth.AuthDtos.MessageResponse;
import dev.servicelog.auth.AuthDtos.ResetPasswordRequest;
import dev.servicelog.auth.AuthDtos.TokenRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Everything here except /me is public (see SecurityConfig). Logging out is client-side:
// the app discards its token, and every token expires on its own (see app.jwt.expiration-ms).
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Same reply whether or not the email has an account
    private static final MessageResponse CHECK_INBOX =
            new MessageResponse("If that email has an account, we've sent a link. Check your inbox.");

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        authService.verifyEmail(request.token());
        return new MessageResponse("Email verified. You can log in now.");
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.resendVerification(request.email());
        return CHECK_INBOX;
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.email());
        return CHECK_INBOX;
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Password changed. Log in with your new password.");
    }

    // Lets the frontend check that a stored token is still valid when the app opens
    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        return new CurrentUser(authentication.getName());
    }
}
