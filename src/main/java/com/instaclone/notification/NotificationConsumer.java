package com.instaclone.notification;

import com.instaclone.common.NotFoundException;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks up NotificationEvent messages off the Redis Stream, creates the durable Notification row,
 * and pushes it live to a connected recipient over WebSocket. ACKs unconditionally — same
 * reliability scope as com.instaclone.media.MediaUploadConsumer (no retry/DLQ in this phase).
 */
@Component
public class NotificationConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationConsumer(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        try {
            Notification notification = createNotification(body);
            pushLive(notification);
        } catch (Exception e) {
            log.error("Failed to process notification event {}", body, e);
        } finally {
            redisTemplate
                    .opsForStream()
                    .acknowledge(NotificationStreamConfig.STREAM_KEY, NotificationStreamConfig.CONSUMER_GROUP, message.getId());
        }
    }

    @Transactional
    Notification createNotification(Map<String, String> body) {
        User actor = userRepository
                .findById(Long.valueOf(body.get("actorId")))
                .orElseThrow(() -> new NotFoundException("User not found"));

        Notification notification = new Notification();
        notification.setRecipient(userRepository.getReferenceById(Long.valueOf(body.get("recipientId"))));
        notification.setActor(actor);
        notification.setType(NotificationType.valueOf(body.get("type")));
        notification.setTargetType(body.get("targetType"));
        notification.setTargetId(Long.valueOf(body.get("targetId")));
        notification.setCreatedAt(Instant.now());
        return notificationRepository.save(notification);
    }

    private void pushLive(Notification notification) {
        NotificationResponse response = NotificationResponse.from(notification, UserSummary.from(notification.getActor()));
        messagingTemplate.convertAndSendToUser(
                String.valueOf(notification.getRecipient().getId()), "/queue/notifications", response);
    }
}
