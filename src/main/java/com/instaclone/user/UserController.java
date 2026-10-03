package com.instaclone.user;

import com.instaclone.common.CursorPage;
import com.instaclone.common.PageParams;
import com.instaclone.common.SecurityUtils;
import com.instaclone.social.follow.FollowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    public UserController(UserService userService, FollowService followService) {
        this.userService = userService;
        this.followService = followService;
    }

    @GetMapping("/me/insights")
    public InsightsResponse getInsights(@AuthenticationPrincipal Jwt jwt) {
        return userService.getInsights(SecurityUtils.currentUserId(jwt));
    }

    @GetMapping("/suggestions")
    public List<UserSummary> getSuggestions(
            @RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return userService.getSuggestions(SecurityUtils.currentUserId(jwt), PageParams.clamp(limit));
    }

    @GetMapping("/me/follow-requests")
    public List<UserSummary> getFollowRequests(
            @RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return userService.getFollowRequests(SecurityUtils.currentUserId(jwt), PageParams.clamp(limit));
    }

    @DeleteMapping("/me/followers/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFollower(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        followService.removeFollower(SecurityUtils.currentUserId(jwt), username);
    }

    @GetMapping("/{username}")
    public UserProfileResponse getProfile(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(username, SecurityUtils.currentUserId(jwt));
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        return userService.updateProfile(SecurityUtils.currentUserId(jwt), request);
    }

    @GetMapping("/{username}/followers")
    public CursorPage<UserSummary> getFollowers(
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.getFollowers(username, SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }

    @GetMapping("/{username}/following")
    public CursorPage<UserSummary> getFollowing(
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.getFollowing(username, SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }
}
