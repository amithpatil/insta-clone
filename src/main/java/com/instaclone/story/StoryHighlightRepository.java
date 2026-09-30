package com.instaclone.story;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryHighlightRepository extends JpaRepository<StoryHighlight, Long> {

    List<StoryHighlight> findByUserIdOrderByCreatedAtDesc(Long userId);
}
