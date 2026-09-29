package com.instaclone.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque keyset-pagination cursor encoding the last row's (createdAt, id) seen by the client.
 * Using both fields (not just createdAt) makes the ordering total even when timestamps tie.
 */
public record Cursor(Instant createdAt, long id) {

    public String encode() {
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (Exception e) {
            throw new BadRequestException("Invalid cursor");
        }
    }
}
