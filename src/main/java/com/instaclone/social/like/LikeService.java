package com.instaclone.social.like;

import com.instaclone.common.NotFoundException;
import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.user.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public LikeService(LikeRepository likeRepository, PostRepository postRepository, UserRepository userRepository) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public LikeCountResponse likePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));

        boolean alreadyLiked =
                likeRepository.existsByUserIdAndLikeableTypeAndLikeableId(userId, LikeableType.POST, postId);
        if (!alreadyLiked) {
            Like like = new Like();
            like.setUser(userRepository.getReferenceById(userId));
            like.setLikeableType(LikeableType.POST);
            like.setLikeableId(postId);
            like.setCreatedAt(Instant.now());
            likeRepository.save(like);

            post.incrementLikeCount();
        }

        return new LikeCountResponse(post.getLikeCount(), true);
    }

    @Transactional
    public LikeCountResponse unlikePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));

        likeRepository
                .findByUserIdAndLikeableTypeAndLikeableId(userId, LikeableType.POST, postId)
                .ifPresent(like -> {
                    likeRepository.delete(like);
                    post.decrementLikeCount();
                });

        return new LikeCountResponse(post.getLikeCount(), false);
    }
}
