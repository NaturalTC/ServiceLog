package dev.servicelog.photo;

import java.time.Instant;

public final class PhotoDtos {

    private PhotoDtos() {
    }

    // Links are signed and expire (see PhotoLinks), so fetch the list again rather than storing them
    public record PhotoResponse(Long id, String url, String thumbUrl, int width, int height, Instant createdAt) {
    }
}
