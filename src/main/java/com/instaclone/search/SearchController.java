package com.instaclone.search;

import com.instaclone.common.PageParams;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/users")
    public List<UserSearchResult> searchUsers(@RequestParam String q, @RequestParam(required = false) Integer limit) {
        return searchService.searchUsers(q, PageParams.clamp(limit));
    }

    @GetMapping("/posts")
    public List<PostSearchResult> searchPosts(@RequestParam String q, @RequestParam(required = false) Integer limit) {
        return searchService.searchPosts(q, PageParams.clamp(limit));
    }
}
