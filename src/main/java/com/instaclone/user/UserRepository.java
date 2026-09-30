package com.instaclone.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findAllByUsernameIn(Collection<String> usernames);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("select u from User u where u.username = :value or u.email = :value")
    Optional<User> findByUsernameOrEmail(@Param("value") String value);
}
