package dev.servicelog.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

// Creates and redeems the single-use tokens in verification and reset links
@Service
public class AuthTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits: impossible to guess

    private final AuthTokenRepository tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthTokenService(AuthTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    // Returns the raw token for the email link. Any older unused token for the same
    // purpose is deleted, so only the most recent link works.
    @Transactional
    public String issue(AppUser user, AuthToken.Purpose purpose, Duration lifetime) {
        tokens.deleteAllFor(user, purpose);

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokens.save(new AuthToken(user, purpose, hash(rawToken), Instant.now(clock).plus(lifetime)));
        return rawToken;
    }

    // Checks the token and deletes it so it can't be used twice. Returns the account it belongs to.
    @Transactional
    public AppUser redeem(String rawToken, AuthToken.Purpose purpose) {
        AuthToken token = tokens.findByTokenHashAndPurpose(hash(rawToken), purpose)
                .orElseThrow(InvalidTokenException::new);

        tokens.delete(token);
        if (token.isExpired(Instant.now(clock))) {
            throw new InvalidTokenException();
        }
        return token.getUser();
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
