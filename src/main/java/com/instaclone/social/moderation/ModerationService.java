package com.instaclone.social.moderation;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.NotFoundException;
import com.instaclone.social.follow.FollowRepository;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {

    private final UserModerationRepository moderationRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public ModerationService(
            UserModerationRepository moderationRepository, FollowRepository followRepository, UserRepository userRepository) {
        this.moderationRepository = moderationRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void block(Long actorId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getId().equals(actorId)) {
            throw new BadRequestException("You cannot block yourself");
        }
        if (moderationRepository.existsByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.BLOCK)) {
            return;
        }
        User actor = userRepository.getReferenceById(actorId);

        UserModeration block = new UserModeration();
        block.setActor(actor);
        block.setTarget(target);
        block.setType(ModerationType.BLOCK);
        block.setCreatedAt(Instant.now());
        moderationRepository.save(block);

        // A block supersedes a restrict of the same pair — drop a stale restrict row either
        // direction so it doesn't linger once the stronger relationship exists.
        moderationRepository
                .findByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.RESTRICT)
                .ifPresent(moderationRepository::delete);

        // Blocking severs the follow relationship both directions, mirroring real Instagram —
        // a block that left an existing follow in place would still leak the blocked party's
        // posts into the blocker's home feed (FeedService.getHomeFeed is purely follow-based).
        followRepository.findByFollowerIdAndFolloweeId(actorId, target.getId()).ifPresent(followRepository::delete);
        followRepository.findByFollowerIdAndFolloweeId(target.getId(), actorId).ifPresent(followRepository::delete);
    }

    @Transactional
    public void unblock(Long actorId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new NotFoundException("User not found"));
        moderationRepository
                .findByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.BLOCK)
                .ifPresent(moderationRepository::delete);
    }

    @Transactional
    public void restrict(Long actorId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getId().equals(actorId)) {
            throw new BadRequestException("You cannot restrict yourself");
        }
        if (moderationRepository.existsByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.RESTRICT)) {
            return;
        }
        User actor = userRepository.getReferenceById(actorId);

        UserModeration restrict = new UserModeration();
        restrict.setActor(actor);
        restrict.setTarget(target);
        restrict.setType(ModerationType.RESTRICT);
        restrict.setCreatedAt(Instant.now());
        moderationRepository.save(restrict);
    }

    @Transactional
    public void unrestrict(Long actorId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new NotFoundException("User not found"));
        moderationRepository
                .findByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.RESTRICT)
                .ifPresent(moderationRepository::delete);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherDirection(Long aId, Long bId) {
        return moderationRepository.existsBlockEitherDirection(aId, bId);
    }

    /** Directional: has targetId blocked viewerId? Deliberately NOT "either direction" — a user
     * who blocks someone must retain the ability to view that person's profile (to reach
     * Unblock), while the person they blocked must not be able to view theirs at all. */
    @Transactional(readOnly = true)
    public boolean isBlockedBy(Long targetId, Long viewerId) {
        return moderationRepository.existsByActorIdAndTargetIdAndType(targetId, viewerId, ModerationType.BLOCK);
    }

    @Transactional(readOnly = true)
    public List<Long> getBlockedEitherDirectionIds(Long userId) {
        return moderationRepository.findBlockedEitherDirectionIds(userId);
    }

    @Transactional(readOnly = true)
    public boolean isRestrictedBy(Long restricterId, Long targetId) {
        return moderationRepository.existsByActorIdAndTargetIdAndType(restricterId, targetId, ModerationType.RESTRICT);
    }
}
