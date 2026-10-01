package com.instaclone.search;

import com.instaclone.config.SearchProperties;
import com.instaclone.social.moderation.ModerationService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final MeilisearchClient client;
    private final SearchProperties props;
    private final ModerationService moderationService;

    public SearchService(MeilisearchClient client, SearchProperties props, ModerationService moderationService) {
        this.client = client;
        this.props = props;
        this.moderationService = moderationService;
    }

    public List<UserSearchResult> searchUsers(String query, int limit, Long viewerId) {
        Set<Long> blockedIds = Set.copyOf(moderationService.getBlockedEitherDirectionIds(viewerId));
        return client.search(props.usersIndex(), query, limit).stream()
                .map(SearchService::toUserResult)
                .filter(result -> !blockedIds.contains(result.id()))
                .toList();
    }

    public List<PostSearchResult> searchPosts(String query, int limit, Long viewerId) {
        Set<Long> blockedIds = Set.copyOf(moderationService.getBlockedEitherDirectionIds(viewerId));
        return client.search(props.postsIndex(), query, limit).stream()
                .map(SearchService::toPostResult)
                .filter(result -> !blockedIds.contains(result.authorId()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static UserSearchResult toUserResult(Map<String, Object> hit) {
        return new UserSearchResult(
                Long.valueOf((String) hit.get("id")),
                (String) hit.get("username"),
                (String) hit.get("fullName"),
                (String) hit.get("profilePictureUrl"),
                Boolean.TRUE.equals(hit.get("isVerified")));
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
