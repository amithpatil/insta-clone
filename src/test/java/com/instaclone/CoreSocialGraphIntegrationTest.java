package com.instaclone;

import static org.assertj.core.api.Assertions.assertThat;

import com.instaclone.auth.AuthRateLimitFilter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Exercises the exact Phase 1 "you'll know it works when" flow over real HTTP against a real
 * (Testcontainers) Postgres and Redis: register two accounts, one posts, the other follows and
 * sees it in their feed, likes it, comments on it, and the denormalized counts update.
 *
 * <p>TestRestTemplate's root URI already includes server.servlet.context-path (/api/v1), so
 * paths below are relative to that — not repeating the prefix.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class CoreSocialGraphIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerPostFollowLikeCommentFeedFlow() {
        String aliceToken = register("alice", "alice@example.com");
        String bobToken = register("bob", "bob@example.com");

        Map<String, Object> media = Map.of("url", uploadUrl(aliceToken, "image/jpeg"), "width", 800, "height", 600);
        Map<String, Object> createPost = Map.of("caption", "hello world", "media", List.of(media));
        ResponseEntity<Map> postResponse =
                rest.exchange("/posts", HttpMethod.POST, new HttpEntity<>(createPost, bearer(aliceToken)), Map.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number postId = (Number) postResponse.getBody().get("id");

        ResponseEntity<Map> followResponse = rest.exchange(
                "/users/alice/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(bobToken)), Map.class);
        assertThat(followResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(followResponse.getBody().get("status")).isEqualTo("ACCEPTED");

        ResponseEntity<Map> feedResponse =
                rest.exchange("/feed", HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), Map.class);
        assertThat(feedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) feedResponse.getBody().get("items");
        assertThat(items).hasSize(1);
        assertThat(((Number) items.get(0).get("id")).longValue()).isEqualTo(postId.longValue());

        rest.exchange(
                "/posts/" + postId + "/likes", HttpMethod.POST, new HttpEntity<>(null, bearer(bobToken)), Map.class);
        rest.exchange(
                "/posts/" + postId + "/comments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("text", "nice!"), bearer(bobToken)),
                Map.class);

        ResponseEntity<Map> postDetail =
                rest.exchange("/posts/" + postId, HttpMethod.GET, new HttpEntity<>(bearer(aliceToken)), Map.class);
        assertThat(((Number) postDetail.getBody().get("likeCount")).intValue()).isEqualTo(1);
        assertThat(((Number) postDetail.getBody().get("commentCount")).intValue()).isEqualTo(1);
    }

    private String register(String username, String email) {
        Map<String, Object> body = Map.of("username", username, "email", email, "password", "password123");
        ResponseEntity<Map> response = rest.postForEntity("/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("accessToken");
    }

    // isOwnedUrl checks the uploader's own id is embedded in the object key, not just the bucket
    // prefix — a hand-built fake URL doesn't pass, so this goes through the real presigned-upload
    // endpoint to get a URL scoped to the given token's user.
    private String uploadUrl(String token, String contentType) {
        ResponseEntity<Map> response = rest.exchange(
                "/posts/upload-url",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("contentType", contentType), bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("publicUrl");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
