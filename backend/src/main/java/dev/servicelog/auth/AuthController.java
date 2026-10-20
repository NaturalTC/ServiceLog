package dev.servicelog.auth;

import dev.servicelog.auth.AuthDtos.CurrentUser;
import dev.servicelog.auth.AuthDtos.LoginRequest;
import dev.servicelog.auth.AuthDtos.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Login is the only public endpoint here. Logging out is client-side: the app discards
// its token, and every token expires on its own (see app.jwt.expiration).
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // Lets the frontend check that a stored token is still valid when the app opens
    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        return new CurrentUser(authentication.getName());
    }
}
