package com.instaclone.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.instaclone.user.User;
import org.junit.jupiter.api.Test;

class PostVisibilityTest {

    @Test
    void publicPostIsVisibleToAnyone() {
        Post post = postBy(author(1L, false));
        assertThat(post.isVisibleTo(user(2L), false)).isTrue();
    }

    @Test
    void publicPostIsVisibleToAnonymousViewer() {
        Post post = postBy(author(1L, false));
        assertThat(post.isVisibleTo(null, false)).isTrue();
    }

    @Test
    void privatePostIsHiddenFromNonFollower() {
        Post post = postBy(author(1L, true));
        assertThat(post.isVisibleTo(user(2L), false)).isFalse();
    }

    @Test
    void privatePostIsHiddenFromAnonymousViewer() {
        Post post = postBy(author(1L, true));
        assertThat(post.isVisibleTo(null, false)).isFalse();
    }

    @Test
    void privatePostIsVisibleToAcceptedFollower() {
        Post post = postBy(author(1L, true));
        assertThat(post.isVisibleTo(user(2L), true)).isTrue();
    }

    @Test
    void privatePostIsAlwaysVisibleToItsOwner() {
        User owner = author(1L, true);
        Post post = postBy(owner);
        assertThat(post.isVisibleTo(owner, false)).isTrue();
    }

    private static Post postBy(User author) {
        Post post = new Post();
        post.setUser(author);
        return post;
    }

    private static User author(Long id, boolean isPrivate) {
        User user = new User();
        user.setId(id);
        user.setPrivate(isPrivate);
        return user;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
