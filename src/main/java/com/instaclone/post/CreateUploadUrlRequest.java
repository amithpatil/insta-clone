package com.instaclone.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUploadUrlRequest(
        @NotBlank @Pattern(regexp = "^image/(jpeg|png|webp)$", message = "Only JPEG, PNG, or WEBP images are accepted")
                String contentType) {}
