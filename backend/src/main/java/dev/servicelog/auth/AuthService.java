package dev.servicelog.auth;

import dev.servicelog.auth.AuthDtos.LoginRequest;
import dev.servicelog.auth.AuthDtos.LoginResponse;
import dev.servicelog.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // A BCrypt hash of a random value. When the email is unknown we still run one BCrypt
    // comparison against it, so a failed login takes the same time either way and response
    // timing can't be used to discover which emails have accounts.
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);

        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !passwordMatches) {
            // Don't log the email: failed logins are where typos of real passwords end up
            log.warn("Login failed");
            throw new InvalidCredentialsException();
        }

        log.info("Login succeeded for user id={}", user.getId());
        return new LoginResponse(jwtService.generateToken(user.getEmail()), jwtService.getExpirationSeconds());
    }
}
