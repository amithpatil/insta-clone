package com.instaclone.post;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByUserId(Long userId);

    // Atomic SQL increments/decrements, not read-modify-write on the entity — two concurrent
    // likes/comments both reading like_count=5 and writing 6 would otherwise lose one update.
    @Modifying
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :postId")
    void incrementLikeCount(@Param("postId") Long postId);

    @Modifying
    @Query("update Post p set p.likeCount = case when p.likeCount > 0 then p.likeCount - 1 else 0 end where p.id = :postId")
    void decrementLikeCount(@Param("postId") Long postId);

    @Modifying
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId")
    void incrementCommentCount(@Param("postId") Long postId);

    @Modifying
    @Query(
            "update Post p set p.commentCount = case when p.commentCount > :amount then p.commentCount - :amount else 0 end "
                    + "where p.id = :postId")
    void decrementCommentCountBy(@Param("postId") Long postId, @Param("amount") long amount);

    // "type != 'REEL' OR media is READY" — a still-transcoding reel has no playable url yet, so it
    // must stay out of every listing until the async worker flips its media row to READY. Photos
    // (never REEL) are unaffected and always pass this check. Two copies (unaliased "posts" table
    // vs. aliased "p") because annotation values must be compile-time constants, so this can't be
    // built with a runtime String.replace() call.
    String READY_FILTER = "(type != 'REEL' OR EXISTS (SELECT 1 FROM media m WHERE m.post_id = posts.id AND m.status = 'READY'))";
    String READY_FILTER_P = "(p.type != 'REEL' OR EXISTS (SELECT 1 FROM media m WHERE m.post_id = p.id AND m.status = 'READY'))";

    @Query(
            value = "SELECT * FROM posts WHERE user_id = :userId AND " + READY_FILTER
                    + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstPageByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM posts WHERE user_id = :userId AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "AND " + READY_FILTER + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findPageByUserIdAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Query(
            value = "SELECT * FROM posts WHERE user_id IN (:userIds) AND " + READY_FILTER
                    + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstPageByUserIds(@Param("userIds") List<Long> userIds, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM posts WHERE user_id IN (:userIds) AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "AND " + READY_FILTER + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findPageByUserIdsAfterCursor(
            @Param("userIds") List<Long> userIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Query(
            value = "SELECT * FROM posts WHERE user_id IN (:userIds) AND type = 'REEL' AND " + READY_FILTER
                    + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstReelsPageByUserIds(@Param("userIds") List<Long> userIds, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM posts WHERE user_id IN (:userIds) AND type = 'REEL' "
                            + "AND (created_at, id) < (:cursorCreatedAt, :cursorId) AND " + READY_FILTER
                            + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findReelsPageByUserIdsAfterCursor(
            @Param("userIds") List<Long> userIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // Explore: public accounts only (never a private account regardless of follow state), excluding
    // the viewer and anyone they already follow, ranked by like_count over the trailing window.
    // excludedIds must always include the viewer's own id (see FeedService) so this NOT IN never
    // receives an empty list, which native Postgres rejects as invalid syntax.
    @Query(
            value =
                    "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
                            + "WHERE u.is_private = false AND p.user_id NOT IN (:excludedIds) "
                            + "AND p.created_at > :since AND "
                            + READY_FILTER_P
                            + " ORDER BY p.like_count DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findExploreFirstPage(
            @Param("excludedIds") List<Long> excludedIds, @Param("since") Instant since, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
                            + "WHERE u.is_private = false AND p.user_id NOT IN (:excludedIds) "
                            + "AND p.created_at > :since AND (p.like_count, p.id) < (:cursorRank, :cursorId) AND "
                            + READY_FILTER_P
                            + " ORDER BY p.like_count DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findExploreAfterCursor(
            @Param("excludedIds") List<Long> excludedIds,
            @Param("since") Instant since,
            @Param("cursorRank") long cursorRank,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
