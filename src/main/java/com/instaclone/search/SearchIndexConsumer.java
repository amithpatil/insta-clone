package com.instaclone.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * Picks up SearchIndexEvent messages off the Redis Stream and calls Meilisearch. ACKs
 * unconditionally — same reliability scope as com.instaclone.media/notification (no retry/DLQ).
 */
@Component
public class SearchIndexConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexConsumer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MeilisearchClient meilisearchClient;
    private final StringRedisTemplate redisTemplate;

    public SearchIndexConsumer(MeilisearchClient meilisearchClient, StringRedisTemplate redisTemplate) {
        this.meilisearchClient = meilisearchClient;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        String index = body.get("index");
        String documentId = body.get("documentId");
        try {
            if (Boolean.parseBoolean(body.get("delete"))) {
                meilisearchClient.deleteDocument(index, documentId);
            } else {
                Map<String, Object> fields = objectMapper.readValue(body.get("fields"), Map.class);
                meilisearchClient.upsertDocument(index, fields);
            }
        } catch (Exception e) {
            log.error("Failed to index document {}/{}", index, documentId, e);
        } finally {
            redisTemplate.opsForStream().acknowledge(SearchStreamConfig.STREAM_KEY, SearchStreamConfig.CONSUMER_GROUP, message.getId());
        }
    }
}
