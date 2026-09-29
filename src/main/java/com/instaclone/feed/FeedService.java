package com.instaclone.feed;

import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.post.PostResponse;
import com.instaclone.post.PostService;
import com.instaclone.social.follow.FollowRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Plain DB query for now, matching the build plan's Phase 1 scope — the home feed only moves
 * onto a Redis-backed fan-out-on-write/read hybrid in Phase 6, once there's real volume to
 * justify it.
 */
@Service
public class FeedService {

    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    public FeedService(FollowRepository followRepository, PostRepository postRepository, PostService postService) {
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.postService = postService;
    }

    public CursorPage<PostResponse> getHomeFeed(Long viewerId, String cursor, int limit) {
        List<Long> followedIds = followRepository.findAcceptedFolloweeIds(viewerId);
        if (followedIds.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        List<Post> rows = cursor == null
                ? postRepository.findFirstPageByUserIds(followedIds, limit + 1)
                : postRepository.findPageByUserIdsAfterCursor(
                        followedIds, Cursor.decode(cursor).createdAt(), Cursor.decode(cursor).id(), limit + 1);

        return postService.toPage(rows, limit, viewerId);
    }
}
