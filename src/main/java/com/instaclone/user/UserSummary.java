package com.instaclone.user;

public record UserSummary(Long id, String username, String fullName, String profilePictureUrl) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getFullName(), user.getProfilePictureUrl());
    }
}
