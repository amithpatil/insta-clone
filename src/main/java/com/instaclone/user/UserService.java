package com.instaclone.user;

import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.post.PostRepository;
import com.instaclone.social.follow.FollowRepository;
import com.instaclone.social.follow.FollowStatus;
import com.instaclone.social.follow.FollowUserRow;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final ProfileVisibilityService profileVisibilityService;

    public UserService(
            UserRepository userRepository,
            FollowRepository followRepository,
            PostRepository postRepository,
            ProfileVisibilityService profileVisibilityService) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.profileVisibilityService = profileVisibilityService;
    }

    public User findByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public User findByIdOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UserProfileResponse getProfile(String username, Long viewerId) {
        User target = findByUsernameOrThrow(username);
        return toProfileResponse(target, viewerId);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findByIdOrThrow(userId);
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.profilePictureUrl() != null) {
            user.setProfilePictureUrl(request.profilePictureUrl());
        }
        if (request.isPrivate() != null) {
            user.setPrivate(request.isPrivate());
        }
        user.setUpdatedAt(Instant.now());
        return toProfileResponse(user, userId);
    }

    public CursorPage<UserSummary> getFollowers(String username, Long viewerId, String cursor, int limit) {
        User target = findByUsernameOrThrow(username);
        assertVisible(target, viewerId);
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<FollowUserRow> rows = decoded == null
                ? followRepository.findFirstPageFollowers(target.getId(), limit + 1)
                : followRepository.findPageFollowersAfterCursor(
                        target.getId(), decoded.createdAt(), decoded.id(), limit + 1);
        return toUserSummaryPage(rows, limit);
    }

    public CursorPage<UserSummary> getFollowing(String username, Long viewerId, String cursor, int limit) {
        User target = findByUsernameOrThrow(username);
        assertVisible(target, viewerId);
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<FollowUserRow> rows = decoded == null
                ? followRepository.findFirstPageFollowing(target.getId(), limit + 1)
                : followRepository.findPageFollowingAfterCursor(
                        target.getId(), decoded.createdAt(), decoded.id(), limit + 1);
        return toUserSummaryPage(rows, limit);
    }

    private void assertVisible(User target, Long viewerId) {
        User viewer = findByIdOrThrow(viewerId);
        if (!profileVisibilityService.isVisible(target, viewer)) {
            throw new ForbiddenException("This account is private");
        }
    }

    private CursorPage<UserSummary> toUserSummaryPage(List<FollowUserRow> rows, int limit) {
        CursorPage<FollowUserRow> page =
                CursorPage.of(rows, limit, r -> new Cursor(r.getFollowCreatedAt(), r.getFollowId()).encode());
        List<UserSummary> items = page.items().stream()
                .map(r -> new UserSummary(r.getUserId(), r.getUsername(), r.getFullName(), r.getProfilePictureUrl()))
                .toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    private UserProfileResponse toProfileResponse(User target, Long viewerId) {
        long postCount = postRepository.countByUserId(target.getId());
        long followerCount = followRepository.countByFolloweeIdAndStatus(target.getId(), FollowStatus.ACCEPTED);
        long followingCount = followRepository.countByFollowerIdAndStatus(target.getId(), FollowStatus.ACCEPTED);

        ViewerRelationship relationship = ViewerRelationship.NOT_FOLLOWING;
        if (viewerId != null) {
            if (viewerId.equals(target.getId())) {
                relationship = ViewerRelationship.SELF;
            } else {
                relationship = followRepository
                        .findByFollowerIdAndFolloweeId(viewerId, target.getId())
                        .map(f -> f.getStatus() == FollowStatus.ACCEPTED
                                ? ViewerRelationship.FOLLOWING
                                : ViewerRelationship.REQUESTED)
                        .orElse(ViewerRelationship.NOT_FOLLOWING);
            }
        }

        return new UserProfileResponse(
                target.getId(),
                target.getUsername(),
                target.getFullName(),
                target.getBio(),
                target.getProfilePictureUrl(),
                target.isPrivate(),
                target.isVerified(),
                postCount,
                followerCount,
                followingCount,
                relationship);
    }
}
