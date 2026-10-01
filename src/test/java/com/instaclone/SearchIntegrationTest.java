package com.instaclone;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies the async indexing pipeline end-to-end against a real Meilisearch instance — same
 * pattern as ReelsExploreIntegrationTest's SeaweedFS container, since no official Testcontainers
 * Meilisearch module exists (checked on Maven Central).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SearchIntegrationTest {

    private static final GenericContainer<?> MEILISEARCH =
            new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.54.2"))
                    .withEnv("MEILI_MASTER_KEY", "test-master-key")
                    .withEnv("MEILI_ENV", "development")
                    .withExposedPorts(7700);

    @DynamicPropertySource
    static void searchProperties(DynamicPropertyRegistry registry) {
        MEILISEARCH.start();
        String base = "http://" + MEILISEARCH.getHost() + ":" + MEILISEARCH.getMappedPort(7700);
        registry.add("app.search.endpoint", () -> base);
        registry.add("app.search.api-key", () -> "test-master-key");
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void postsAndUsersBecomeSearchableAfterCreation() throws InterruptedException {
        String aliceToken = register("search_alice", "search_alice@example.com");

        ResponseEntity<Map> postResponse = createPhotoPost(aliceToken, "an amazing sunrise over the mountains");
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number postId = (Number) postResponse.getBody().get("id");

        List<Long> postHits = pollUntil(
                () -> searchPostIds("sunrise", aliceToken), ids -> ids.contains(postId.longValue()));
        assertThat(postHits).contains(postId.longValue());

        List<String> userHits =
                pollUntil(() -> searchUsernames("search_alice", aliceToken), names -> names.contains("search_alice"));
        assertThat(userHits).contains("search_alice");
    }

    @Test
    void privateAccountsPostsAreNeverIndexed() throws InterruptedException {
        String privateToken = register("search_private", "search_private@example.com");
        String viewerToken = register("search_viewer", "search_viewer@example.com");

        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(privateToken)), Map.class);

        ResponseEntity<Map> postResponse = createPhotoPost(privateToken, "a secret unindexable moment");
        Number postId = (Number) postResponse.getBody().get("id");

        // Give the (would-be) indexing pipeline a real chance to run, then confirm it never did.
        Thread.sleep(3000);
        List<Long> hits = searchPostIds("unindexable", viewerToken);
        assertThat(hits).doesNotContain(postId.longValue());
    }

    private <T> T pollUntil(Supplier<T> fetch, Predicate<T> satisfied) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(15);
        T last = fetch.get();
        while (Instant.now().isBefore(deadline)) {
            last = fetch.get();
            if (satisfied.test(last)) {
                return last;
            }
            Thread.sleep(500);
        }
        return last;
    }

    private List<Long> searchPostIds(String query, String token) {
        ResponseEntity<List> response = rest.exchange(
                "/search/posts?q=" + query, HttpMethod.GET, new HttpEntity<>(bearer(token)), List.class);
        List<Map<String, Object>> body = response.getBody();
        return body.stream().map(hit -> ((Number) hit.get("id")).longValue()).toList();
    }

    private List<String> searchUsernames(String query, String token) {
        ResponseEntity<List> response = rest.exchange(
                "/search/users?q=" + query, HttpMethod.GET, new HttpEntity<>(bearer(token)), List.class);
        List<Map<String, Object>> body = response.getBody();
        return body.stream().map(hit -> (String) hit.get("username")).toList();
    }

    private ResponseEntity<Map> createPhotoPost(String token, String caption) {
        Map<String, Object> media = Map.of("url", uploadUrl(token, "image/jpeg"), "width", 800, "height", 600);
        return rest.exchange(
                "/posts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("caption", caption, "media", List.of(media)), bearer(token)),
                Map.class);
    }

    // isOwnedUrl now checks the uploader's own id is embedded in the object key, not just the
    // bucket prefix — a hand-built fake URL no longer passes, so this goes through the real
    // presigned-upload endpoint to get a URL scoped to the given token's user.
    private String uploadUrl(String token, String contentType) {
        ResponseEntity<Map> response = rest.exchange(
                "/posts/upload-url",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("contentType", contentType), bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("publicUrl");
    }

    private String register(String username, String email) {
        Map<String, Object> body = Map.of("username", username, "email", email, "password", "password123");
        ResponseEntity<Map> response = rest.postForEntity("/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("accessToken");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
