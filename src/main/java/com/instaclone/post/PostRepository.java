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

    @Query(
            value = "SELECT * FROM posts WHERE user_id = :userId ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstPageByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM posts WHERE user_id = :userId AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findPageByUserIdAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Query(
            value = "SELECT * FROM posts WHERE user_id IN (:userIds) ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstPageByUserIds(@Param("userIds") List<Long> userIds, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM posts WHERE user_id IN (:userIds) AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findPageByUserIdsAfterCursor(
            @Param("userIds") List<Long> userIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
