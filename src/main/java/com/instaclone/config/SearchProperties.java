package com.instaclone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(String endpoint, String apiKey, String usersIndex, String postsIndex) {}
