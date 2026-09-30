package com.instaclone.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays a SearchIndexEvent onto the Redis Stream only after the triggering transaction commits —
 * same reasoning as MediaStreamPublisher/NotificationStreamPublisher.
 */
@Component
public class SearchStreamPublisher {

    // Plain instance, not an injected bean — Boot 4's auto-configured ObjectMapper is Jackson 3
    // (tools.jackson.databind), not this classic Jackson 2 API. Same fix as MediaProcessingService.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;

    public SearchStreamPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSearchIndex(SearchIndexEvent event) {
        Map<String, String> body = new HashMap<>();
        body.put("index", event.index());
        body.put("documentId", event.documentId());
        body.put("delete", String.valueOf(event.delete()));
        if (!event.delete()) {
            body.put("fields", writeJson(event.fields()));
        }
        redisTemplate.opsForStream().add(SearchStreamConfig.STREAM_KEY, body);
    }

    private String writeJson(Map<String, Object> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize search index fields", e);
        }
    }
}
