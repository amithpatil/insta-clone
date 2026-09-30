package com.instaclone.search;

import com.instaclone.config.SearchProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Same precedent as StorageConfig.ensureBucketExists — idempotent index setup on every startup. */
@Configuration
public class SearchConfig {

    private static final Logger log = LoggerFactory.getLogger(SearchConfig.class);

    @Bean
    public CommandLineRunner ensureSearchIndexes(MeilisearchClient client, SearchProperties props) {
        return args -> {
            try {
                client.createIndexIfMissing(props.usersIndex(), "id");
                client.updateSearchableAttributes(props.usersIndex(), List.of("username", "fullName"));

                client.createIndexIfMissing(props.postsIndex(), "id");
                client.updateSearchableAttributes(
                        props.postsIndex(), List.of("caption", "hashtags", "authorUsername"));
            } catch (Exception e) {
                log.warn("Could not verify/create Meilisearch indexes: {}", e.getMessage());
            }
        };
    }
}
