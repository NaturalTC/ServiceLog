package dev.servicelog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

// Issues and checks the JSON Web Tokens that keep the API stateless: after login,
// every request proves who it is with a signed token instead of a server session.
@Service
public class JwtService {

    // HS256 needs a key of at least 256 bits (32 bytes)
    private static final int MIN_SECRET_BYTES = 32;
    private static final String SESSION_VERSION_CLAIM = "sv";

    public record TokenClaims(String email, int sessionVersion) {
    }

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Fail at startup rather than run with a weak or missing key
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = expirationMs;
    }

    // sessionVersion ties the token to the account's current password (see AppUser.changePassword)
    public String generateToken(String email, int sessionVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(SESSION_VERSION_CLAIM, sessionVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    // Returns the token's claims if it is well-formed, correctly signed and not expired;
    // empty otherwise. Parsing once does all three checks.
    public Optional<TokenClaims> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer sessionVersion = claims.get(SESSION_VERSION_CLAIM, Integer.class);
            if (claims.getSubject() == null || sessionVersion == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(claims.getSubject(), sessionVersion));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}
