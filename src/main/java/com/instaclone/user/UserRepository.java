package com.instaclone.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findAllByUsernameIn(Collection<String> usernames);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("select u from User u where u.username = :value or u.email = :value")
    Optional<User> findByUsernameOrEmail(@Param("value") String value);

    // Ranked by follower count (a plain popularity signal, no personalization yet) — excludedIds
    // must always include the viewer's own id (same "NOT IN never receives an empty list" rule
    // PostRepository's explore query already relies on).
    @Query(
            value =
                    "SELECT u.* FROM users u "
                            + "LEFT JOIN (SELECT followee_id, COUNT(*) AS c FROM follows WHERE status = 'ACCEPTED' GROUP BY followee_id) fc "
                            + "ON fc.followee_id = u.id "
                            + "WHERE u.id NOT IN (:excludedIds) "
                            + "ORDER BY COALESCE(fc.c, 0) DESC, u.id DESC LIMIT :limit",
            nativeQuery = true)
    List<User> findSuggestions(@Param("excludedIds") List<Long> excludedIds, @Param("limit") int limit);
}
