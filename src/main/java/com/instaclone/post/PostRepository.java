package com.instaclone.post;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByUserId(Long userId);

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
