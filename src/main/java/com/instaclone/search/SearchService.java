package com.instaclone.search;

import com.instaclone.config.SearchProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final MeilisearchClient client;
    private final SearchProperties props;

    public SearchService(MeilisearchClient client, SearchProperties props) {
        this.client = client;
        this.props = props;
    }

    public List<UserSearchResult> searchUsers(String query, int limit) {
        return client.search(props.usersIndex(), query, limit).stream().map(SearchService::toUserResult).toList();
    }

    public List<PostSearchResult> searchPosts(String query, int limit) {
        return client.search(props.postsIndex(), query, limit).stream().map(SearchService::toPostResult).toList();
    }

    @SuppressWarnings("unchecked")
    private static UserSearchResult toUserResult(Map<String, Object> hit) {
        return new UserSearchResult(
                Long.valueOf((String) hit.get("id")),
                (String) hit.get("username"),
                (String) hit.get("fullName"),
                (String) hit.get("profilePictureUrl"));
    }

    @SuppressWarnings("unchecked")
    private static PostSearchResult toPostResult(Map<String, Object> hit) {
        List<String> hashtags = hit.get("hashtags") instanceof List<?> list ? (List<String>) list : List.of();
        return new PostSearchResult(
                Long.valueOf((String) hit.get("id")),
                (String) hit.get("caption"),
                Long.valueOf((String) hit.get("authorId")),
                (String) hit.get("authorUsername"),
                hashtags,
                (String) hit.get("createdAt"));
    }
}
