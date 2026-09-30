package com.instaclone.feed;

import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.RankCursor;
import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.post.PostResponse;
import com.instaclone.post.PostService;
import com.instaclone.report.ReportRepository;
import com.instaclone.social.follow.FollowRepository;
import com.instaclone.social.moderation.ModerationService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain DB query for now, matching the build plan's Phase 1 scope — the home feed only moves
 * onto a Redis-backed fan-out-on-write/read hybrid in Phase 6, once there's real volume to
 * justify it.
 */
@Service
public class FeedService {

    private static final int EXPLORE_WINDOW_DAYS = 7;

    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final ModerationService moderationService;
    private final ReportRepository reportRepository;

    public FeedService(
            FollowRepository followRepository,
            PostRepository postRepository,
            PostService postService,
            ModerationService moderationService,
            ReportRepository reportRepository) {
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.moderationService = moderationService;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getHomeFeed(Long viewerId, String cursor, int limit) {
        List<Long> followedIds = followRepository.findAcceptedFolloweeIds(viewerId);
        if (followedIds.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Post> rows = decoded == null
                ? postRepository.findFirstPageByUserIds(followedIds, limit + 1)
                : postRepository.findPageByUserIdsAfterCursor(followedIds, decoded.createdAt(), decoded.id(), limit + 1);

        return postService.toPage(excludeReported(rows, viewerId), limit, viewerId);
    }

    /** A reported post (see ReportService) is filtered out of the two passive/algorithmic surfaces
     * — direct navigation (profile grid, a shared link) still shows it, since the report is a
     * "don't surface this to me again" signal, not a ban. Filtered post-fetch rather than in the
     * native query, matching the row-count approximation CommentService already accepts for
     * restricted comments — an edge case, not a correctness requirement here. */
    private List<Post> excludeReported(List<Post> rows, Long viewerId) {
        Set<Long> reportedPostIds = new HashSet<>(reportRepository.findReportedPostIds(viewerId));
        if (reportedPostIds.isEmpty()) {
            return rows;
        }
        return rows.stream().filter(p -> !reportedPostIds.contains(p.getId())).toList();
    }

    /**
     * Most-liked posts from public accounts the viewer doesn't already follow, over the trailing
     * window — the one global/algorithmic surface in Phase 2. Deliberately checks is_private
     * directly rather than "not followed": a private account must never leak into a discovery
     * feed for a non-follower, the same class of bug fixed twice in the Phase 1 review.
     */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getExploreFeed(Long viewerId, String cursor, int limit) {
        List<Long> excludedIds = new ArrayList<>(followRepository.findAcceptedFolloweeIds(viewerId));
        excludedIds.add(viewerId);
        excludedIds.addAll(moderationService.getBlockedEitherDirectionIds(viewerId));
        Instant since = Instant.now().minus(EXPLORE_WINDOW_DAYS, ChronoUnit.DAYS);

        RankCursor decoded = cursor == null ? null : RankCursor.decode(cursor);
        List<Post> rows = decoded == null
                ? postRepository.findExploreFirstPage(excludedIds, since, limit + 1)
                : postRepository.findExploreAfterCursor(
                        excludedIds, since, decoded.rank(), decoded.id(), limit + 1);
        rows = excludeReported(rows, viewerId);

        CursorPage<Post> page =
                CursorPage.of(rows, limit, p -> new RankCursor(p.getLikeCount(), p.getId()).encode());
        List<PostResponse> items = postService.enrich(page.items(), viewerId);
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }
}
