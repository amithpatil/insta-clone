package com.instaclone.notification;

import com.instaclone.common.NotFoundException;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out of NotificationConsumer so @Transactional actually applies — a same-class method call
 * (as this used to be, invoked directly from NotificationConsumer.onMessage) bypasses Spring's
 * AOP proxy entirely, silently making the annotation a no-op.
 */
@Service
class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    NotificationWriter(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
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
}
