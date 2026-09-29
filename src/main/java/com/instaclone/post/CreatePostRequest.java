package com.instaclone.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @Size(max = 2200) String caption, @Size(max = 255) String location, @NotNull @Valid MediaItem media) {

    public record MediaItem(@NotNull String url, Integer width, Integer height) {}
}
