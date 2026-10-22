package dev.servicelog.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

// Signed, expiring links to photo files. An <img> tag can't send the login token, so each
// link carries its own proof instead: an HMAC of the photo, the size and an expiry time.
//
// Links stay the same for a whole hour (the expiry is rounded), so the browser can cache
// the images, and they stop working one to two hours after they were handed out.
@Component
public class PhotoLinks {

    private static final String PURPOSE = "photo:"; // keeps these apart from other HMAC uses
    private static final long HOUR = 3600;

    private final SecretKeySpec key;
    private final Clock clock;

    public PhotoLinks(@Value("${app.jwt.secret}") String secret, Clock clock) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.clock = clock;
    }

    public String url(long photoId, String size) {
        long expires = (now() / HOUR + 2) * HOUR;
        return "/api/photos/" + photoId + "/" + size + "?exp=" + expires + "&sig=" + signature(photoId, size, expires);
    }

    public boolean isValid(long photoId, String size, long expires, String sig) {
        if (sig == null || expires < now()) {
            return false;
        }
        byte[] expected = signature(photoId, size, expires).getBytes(StandardCharsets.US_ASCII);
        // Constant-time comparison, so response timing can't leak the signature
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII));
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    private String signature(long photoId, String size, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal((PURPOSE + photoId + ":" + size + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }
}
