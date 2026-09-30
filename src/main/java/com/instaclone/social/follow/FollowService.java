package com.instaclone.social.follow;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.ConflictException;
import com.instaclone.common.NotFoundException;
import com.instaclone.notification.NotificationEvent;
import com.instaclone.notification.NotificationType;
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
    private final ApplicationEventPublisher eventPublisher;

    public FollowService(
            FollowRepository followRepository, UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FollowStatusResponse follow(Long followerId, String followeeUsername) {
        User followee = userRepository.findByUsername(followeeUsername).orElseThrow(() -> new NotFoundException("User not found"));
        if (followee.getId().equals(followerId)) {
            throw new BadRequestException("You cannot follow yourself");
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

        eventPublisher.publishEvent(
                new NotificationEvent(followee.getId(), followerId, NotificationType.FOLLOW, "USER", followerId));

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
        return new FollowStatusResponse(follow.getStatus());
    }
}
