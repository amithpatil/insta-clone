package com.instaclone.auth;

import com.instaclone.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Refresh tokens are opaque random strings, never JWTs — only their SHA-256 hash is stored, as
 * "refresh:{hash}" -> userId in Redis with a TTL matching the token's lifetime. This gives
 * server-side revocation (delete the key) and expiry (TTL) for free, with nothing sensitive
 * persisted if Redis were ever exposed.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.ttl = jwtProperties.refreshTokenTtl();
    }

    public String issue(Long userId) {
        String rawToken = generateToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + hash(rawToken), String.valueOf(userId), ttl);
        return rawToken;
    }

    public Optional<Long> consume(String rawToken) {
        String key = KEY_PREFIX + hash(rawToken);
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            return Optional.empty();
        }
        redisTemplate.delete(key);
        return Optional.of(Long.valueOf(userId));
    }

    public void revoke(String rawToken) {
        redisTemplate.delete(KEY_PREFIX + hash(rawToken));
    }

    public Duration ttl() {
        return ttl;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
