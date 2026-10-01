package com.instaclone.social.follow;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.ConflictException;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.notification.NotificationEvent;
import com.instaclone.notification.NotificationType;
import com.instaclone.social.moderation.ModerationService;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ModerationService moderationService;
    private final ApplicationEventPublisher eventPublisher;

    public FollowService(
            FollowRepository followRepository,
            UserRepository userRepository,
            ModerationService moderationService,
            ApplicationEventPublisher eventPublisher) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.moderationService = moderationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FollowStatusResponse follow(Long followerId, String followeeUsername) {
        User followee = userRepository.findByUsername(followeeUsername).orElseThrow(() -> new NotFoundException("User not found"));
        if (followee.getId().equals(followerId)) {
            throw new BadRequestException("You cannot follow yourself");
        }
        if (moderationService.isBlockedEitherDirection(followerId, followee.getId())) {
            throw new ForbiddenException("You cannot follow this account");
        }
        if (followRepository.findByFollowerIdAndFolloweeId(followerId, followee.getId()).isPresent()) {
            throw new ConflictException("Already following, or a follow request is already pending");
        }

        User follower = userRepository.findById(followerId).orElseThrow(() -> new NotFoundException("User not found"));

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowee(followee);
        follow.setStatus(followee.isPrivate() ? FollowStatus.PENDING : FollowStatus.ACCEPTED);
        follow.setCreatedAt(Instant.now());
        followRepository.save(follow);

        // A public account's follow takes effect immediately ("followed you"); a private account's
        // request is merely PENDING until approved ("requested to follow you") — the two need
        // distinct notification types so the recipient (and the frontend copy) can tell them apart.
        NotificationType notificationType =
                follow.getStatus() == FollowStatus.ACCEPTED ? NotificationType.FOLLOW : NotificationType.FOLLOW_REQUEST;
        eventPublisher.publishEvent(
                new NotificationEvent(followee.getId(), followerId, notificationType, "USER", followerId));

        return new FollowStatusResponse(follow.getStatus());
    }

    @Transactional
    public void unfollow(Long followerId, String followeeUsername) {
        User followee = userRepository.findByUsername(followeeUsername).orElseThrow(() -> new NotFoundException("User not found"));
        followRepository
                .findByFollowerIdAndFolloweeId(followerId, followee.getId())
                .ifPresent(followRepository::delete);
    }

    @Transactional
    public FollowStatusResponse acceptFollowRequest(Long approverId, String followerUsername) {
        User follower = userRepository.findByUsername(followerUsername).orElseThrow(() -> new NotFoundException("User not found"));
        Follow follow = followRepository
                .findByFollowerIdAndFolloweeId(follower.getId(), approverId)
                .filter(f -> f.getStatus() == FollowStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("No pending follow request from this user"));
        follow.accept();

        // Distinct from NotificationType.FOLLOW: the approver didn't follow the requester back
        // (no reciprocal Follow row is created here), they just approved an existing request.
        eventPublisher.publishEvent(new NotificationEvent(
                follower.getId(), approverId, NotificationType.FOLLOW_REQUEST_ACCEPTED, "USER", approverId));

        return new FollowStatusResponse(follow.getStatus());
    }

    @Transactional
    public void rejectFollowRequest(Long approverId, String followerUsername) {
        User follower = userRepository.findByUsername(followerUsername).orElseThrow(() -> new NotFoundException("User not found"));
        Follow follow = followRepository
                .findByFollowerIdAndFolloweeId(follower.getId(), approverId)
                .filter(f -> f.getStatus() == FollowStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("No pending follow request from this user"));
        followRepository.delete(follow);
    }
}
