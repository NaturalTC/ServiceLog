package dev.servicelog.tuneup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

// Tokens for unsubscribe links: "<customerId>.<signature>", where the signature is an
// HMAC of the customer id. Nothing to store, the same link works in every email, and
// nobody can unsubscribe someone else by changing the number.
@Component
public class UnsubscribeTokens {

    private static final String PURPOSE = "unsubscribe-reminders:"; // keeps these apart from other HMAC uses
    private final SecretKeySpec key;

    public UnsubscribeTokens(@Value("${app.jwt.secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String tokenFor(long customerId) {
        return customerId + "." + signature(customerId);
    }

    // The customer id inside a valid token; empty for anything forged or malformed
    public Optional<Long> customerIdFrom(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        long customerId;
        try {
            customerId = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        byte[] expected = signature(customerId).getBytes(StandardCharsets.US_ASCII);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        // Constant-time comparison, so response timing can't leak the signature
        return MessageDigest.isEqual(expected, given) ? Optional.of(customerId) : Optional.empty();
    }

    private String signature(long customerId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal((PURPOSE + customerId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }
}
