package com.instaclone.search;

import com.instaclone.common.RedisStreamGroupBootstrapper;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

/**
 * Third instance of the same Redis Streams shape as com.instaclone.media / com.instaclone.notification
 * — a stream/consumer-group pair for a third concern. The identical "bootstrap the consumer group"
 * boilerplate is shared via RedisStreamGroupBootstrapper; the publish/consume logic stays separate
 * per pipeline, since those genuinely differ (see the class comment on the bootstrapper).
 */
@Configuration
public class SearchStreamConfig {

    public static final String STREAM_KEY = "search-index";
    public static final String CONSUMER_GROUP = "search-index-workers";
    private static final String CONSUMER_NAME = "search-index-worker-1";

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> searchStreamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            SearchIndexConsumer consumer,
            RedisStreamGroupBootstrapper bootstrapper) {
        bootstrapper.ensureConsumerGroup(redisTemplate, STREAM_KEY, CONSUMER_GROUP);

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                consumer);

        return container;
    }
}
