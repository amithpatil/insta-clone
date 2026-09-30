package com.instaclone.search;

import com.instaclone.config.SearchProperties;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over Meilisearch's plain REST API — deliberately not a third-party SDK, since the
 * handful of calls this needs (upsert/delete a document, search, configure an index) are simple
 * JSON over HTTP, the same reasoning S3 access goes through the AWS SDK directly rather than a
 * heavier abstraction.
 *
 * <p>Meilisearch's write endpoints are asynchronous (a 202 just means "task enqueued" — verified
 * live: creating an already-existing index returns 202 immediately, and only the task itself
 * later fails with index_already_exists). Idempotent index creation therefore checks existence
 * first via GET, which is synchronous, rather than trying to interpret the write's own response.
 */
@Component
public class MeilisearchClient {

    private final RestClient restClient;

    public MeilisearchClient(SearchProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.endpoint())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();
    }

    public void createIndexIfMissing(String indexUid, String primaryKey) {
        try {
            restClient.get().uri("/indexes/{index}", indexUid).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            restClient
                    .post()
                    .uri("/indexes")
                    .body(Map.of("uid", indexUid, "primaryKey", primaryKey))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public void updateSearchableAttributes(String indexUid, List<String> attributes) {
        restClient
                .put()
                .uri("/indexes/{index}/settings/searchable-attributes", indexUid)
                .body(attributes)
                .retrieve()
                .toBodilessEntity();
    }

    public void upsertDocument(String indexUid, Map<String, Object> document) {
        restClient
                .put()
                .uri("/indexes/{index}/documents", indexUid)
                .body(List.of(document))
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteDocument(String indexUid, String documentId) {
        restClient.delete().uri("/indexes/{index}/documents/{id}", indexUid, documentId).retrieve().toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String indexUid, String query, int limit) {
        Map<String, Object> response = restClient
                .post()
                .uri("/indexes/{index}/search", indexUid)
                .body(Map.of("q", query, "limit", limit))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        Object hits = response == null ? null : response.get("hits");
        return hits instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
