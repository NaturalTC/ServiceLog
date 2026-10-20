package dev.servicelog.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private final JwtService jwtService = new JwtService(SECRET, ONE_HOUR_MS);

    @Test
    void validToken_returnsEmail() {
        String token = jwtService.generateToken("owner@example.com");

        assertEquals("owner@example.com", jwtService.validateAndGetEmail(token).orElseThrow());
    }

    @Test
    void garbageToken_isRejected() {
        assertTrue(jwtService.validateAndGetEmail("this.is.garbage").isEmpty());
    }

    @Test
    void expiredToken_isRejected() {
        // Negative expiration: the token is already expired when it's created
        JwtService expiring = new JwtService(SECRET, -1000L);
        String token = expiring.generateToken("owner@example.com");

        assertTrue(jwtService.validateAndGetEmail(token).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        JwtService attacker = new JwtService("a-completely-different-secret-of-32-bytes", ONE_HOUR_MS);
        String forged = attacker.generateToken("owner@example.com");

        assertTrue(jwtService.validateAndGetEmail(forged).isEmpty());
    }

    @Test
    void tamperedPayload_isRejected() {
        String token = jwtService.generateToken("owner@example.com");
        String[] parts = token.split("\\.");
        // Swap in a payload for a different user but keep the original signature
        String otherPayload = new JwtService(SECRET, ONE_HOUR_MS).generateToken("intruder@example.com").split("\\.")[1];

        assertTrue(jwtService.validateAndGetEmail(parts[0] + "." + otherPayload + "." + parts[2]).isEmpty());
    }

    @Test
    void shortSecret_failsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", ONE_HOUR_MS));
    }
}
