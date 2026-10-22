package dev.servicelog.tuneup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsubscribeTokensTest {

    private final UnsubscribeTokens tokens = new UnsubscribeTokens("test-secret-that-is-at-least-32-bytes-long");

    @Test
    void roundTrip() {
        assertEquals(42L, tokens.customerIdFrom(tokens.tokenFor(42)).orElseThrow());
    }

    @Test
    void sameCustomer_alwaysGetsTheSameLink() {
        assertEquals(tokens.tokenFor(7), tokens.tokenFor(7));
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        String forged = new UnsubscribeTokens("a-completely-different-secret-of-32-bytes").tokenFor(42);

        assertTrue(tokens.customerIdFrom(forged).isEmpty());
    }

    @Test
    void malformedTokens_areRejected() {
        assertTrue(tokens.customerIdFrom(null).isEmpty());
        assertTrue(tokens.customerIdFrom("").isEmpty());
        assertTrue(tokens.customerIdFrom("42").isEmpty());
        assertTrue(tokens.customerIdFrom("abc.def").isEmpty());
        assertTrue(tokens.customerIdFrom(".signature").isEmpty());
    }
}
