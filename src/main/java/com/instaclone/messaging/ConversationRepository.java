package com.instaclone.messaging;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    boolean existsByIdAndParticipantsId(Long conversationId, Long userId);

    // The conversation whose participant set is EXACTLY {userIdA, userIdB} and isn't a group —
    // the HAVING clause requires both target users present, the outer count requires no one else.
    @Query(
            value =
                    "SELECT c.* FROM conversations c WHERE c.is_group = false "
                            + "AND (SELECT COUNT(*) FROM conversation_participants cp2 WHERE cp2.conversation_id = c.id) = 2 "
                            + "AND c.id IN (SELECT cp.conversation_id FROM conversation_participants cp "
                            + "WHERE cp.user_id IN (:userIdA, :userIdB) GROUP BY cp.conversation_id HAVING COUNT(DISTINCT cp.user_id) = 2)",
            nativeQuery = true)
    Optional<Conversation> findOneToOneConversation(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);

    // Ordered by last activity, not creation time; NULLS LAST so a brand-new conversation with no
    // messages yet still shows up (just at the bottom) instead of being pushed around unpredictably.
    @Query(
            value =
                    "SELECT c.* FROM conversations c JOIN conversation_participants cp ON cp.conversation_id = c.id "
                            + "WHERE cp.user_id = :userId "
                            + "ORDER BY (SELECT MAX(m.created_at) FROM messages m WHERE m.conversation_id = c.id) DESC NULLS LAST, "
                            + "c.created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Conversation> findByParticipantIdOrderByLastActivity(@Param("userId") Long userId, @Param("limit") int limit);
}
