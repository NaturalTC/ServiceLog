package dev.servicelog.auth;

import dev.servicelog.auth.AuthDtos.LoginRequest;
import dev.servicelog.auth.AuthDtos.LoginResponse;
import dev.servicelog.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final Duration VERIFY_EMAIL_LIFETIME = Duration.ofHours(24);
    static final Duration RESET_PASSWORD_LIFETIME = Duration.ofMinutes(30);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenService authTokens;
    private final AuthMailer mailer;
    private final LoginAttemptLimiter limiter;

    // A BCrypt hash of a random value. When the email is unknown we still run one BCrypt
    // comparison against it, so a failed login takes the same time either way and response
    // timing can't be used to discover which emails have accounts.
    private final String dummyHash;

    public AuthService(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthTokenService authTokens,
                       AuthMailer mailer,
                       LoginAttemptLimiter limiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authTokens = authTokens;
        this.mailer = mailer;
        this.limiter = limiter;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request, String clientAddress) {
        // Checked before the password, so a blocked attacker learns nothing from further guesses
        limiter.retryAfter(request.email(), clientAddress).ifPresent(wait -> {
            throw new TooManyLoginAttemptsException(wait);
        });

        AppUser user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);

        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !passwordMatches) {
            // Don't log the email: failed logins are where typos of real passwords end up
            log.warn("Login failed");
            limiter.recordFailure(request.email(), clientAddress);
            throw new InvalidCredentialsException();
        }
        if (!user.isEmailVerified()) {
            log.info("Login blocked until email is verified for user id={}", user.getId());
            throw new EmailNotVerifiedException();
        }

        limiter.recordSuccess(request.email());
        log.info("Login succeeded for user id={}", user.getId());
        String token = jwtService.generateToken(user.getEmail(), user.getSessionVersion());
        return new LoginResponse(token, jwtService.getExpirationSeconds());
    }

    @Transactional
    public void sendVerificationEmail(AppUser user) {
        String token = authTokens.issue(user, AuthToken.Purpose.VERIFY_EMAIL, VERIFY_EMAIL_LIFETIME);
        mailer.sendVerification(user.getEmail(), token);
    }

    // Always succeeds from the caller's point of view, so it can't be used to test which emails exist
    @Transactional
    public void resendVerification(String email) {
        users.findByEmailIgnoreCase(email.trim())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
    }

    @Transactional
    public void verifyEmail(String token) {
        AppUser user = authTokens.redeem(token, AuthToken.Purpose.VERIFY_EMAIL);
        user.markEmailVerified();
        log.info("Email verified for user id={}", user.getId());
    }

    // Same rule as resendVerification: the response never depends on whether the account exists.
    // Only verified addresses get reset links, so a reset can't go to an address nobody confirmed.
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmailIgnoreCase(email.trim())
                .filter(AppUser::isEmailVerified)
                .ifPresent(user -> {
                    String token = authTokens.issue(user, AuthToken.Purpose.RESET_PASSWORD, RESET_PASSWORD_LIFETIME);
                    mailer.sendPasswordReset(user.getEmail(), token);
                    log.info("Password reset requested for user id={}", user.getId());
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        AppUser user = authTokens.redeem(token, AuthToken.Purpose.RESET_PASSWORD);
        // Also logs out every existing session: JwtAuthFilter rejects tokens issued before this change
        user.changePassword(passwordEncoder.encode(newPassword));
        log.info("Password reset for user id={}", user.getId());
    }
}
