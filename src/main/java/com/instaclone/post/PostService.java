package com.instaclone.post;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.config.StorageProperties;
import com.instaclone.social.comment.CommentRepository;
import com.instaclone.social.like.LikeRepository;
import com.instaclone.social.like.LikeableType;
import com.instaclone.user.ProfileVisibilityService;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final StorageProperties storageProperties;

    public PostService(
            PostRepository postRepository,
            MediaRepository mediaRepository,
            UserRepository userRepository,
            CommentRepository commentRepository,
            LikeRepository likeRepository,
            ProfileVisibilityService profileVisibilityService,
            StorageProperties storageProperties) {
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.storageProperties = storageProperties;
    }

    @Transactional
    public PostResponse createPost(Long userId, CreatePostRequest request) {
        User author = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        String mediaUrl = request.media().url();
        if (!storageProperties.isOwnedUrl(mediaUrl)) {
            throw new BadRequestException("Media url must reference an object uploaded via /posts/upload-url");
        }

        Post post = new Post();
        post.setUser(author);
        post.setCaption(request.caption());
        post.setLocation(request.location());
        post.setType(PostType.PHOTO);
        post.setMediaCount(1);
        post.setCreatedAt(Instant.now());
        post = postRepository.save(post);

        Media media = new Media();
        media.setPost(post);
        media.setUrl(mediaUrl);
        media.setMediaType(MediaType.IMAGE);
        media.setWidth(request.media().width());
        media.setHeight(request.media().height());
        media.setPosition(0);
        media = mediaRepository.save(media);

        return toResponse(post, UserSummary.from(author), List.of(media), false);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(Long postId, Long viewerId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        assertVisible(post, viewer);

        List<Media> media = mediaRepository.findByPostIdOrderByPosition(postId);
        boolean liked = likeRepository.existsByUserIdAndLikeableTypeAndLikeableId(viewerId, LikeableType.POST, postId);
        return toResponse(post, UserSummary.from(post.getUser()), media, liked);
    }

    @Transactional
    public void deletePost(Long postId, Long requesterId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        if (!post.getUser().getId().equals(requesterId)) {
            throw new ForbiddenException("You can only delete your own posts");
        }

        List<Long> commentIds = commentRepository.findIdsByPostId(postId);
        if (!commentIds.isEmpty()) {
            likeRepository.deleteByLikeableTypeAndLikeableIdIn(LikeableType.COMMENT, commentIds);
        }
        likeRepository.deleteByLikeableTypeAndLikeableId(LikeableType.POST, postId);
        postRepository.delete(post);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getUserPosts(String username, Long viewerId, String cursor, int limit) {
        User author = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!profileVisibilityService.isVisible(author, viewer)) {
            throw new ForbiddenException("This account is private");
        }

        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Post> rows = decoded == null
                ? postRepository.findFirstPageByUserId(author.getId(), limit + 1)
                : postRepository.findPageByUserIdAfterCursor(
                        author.getId(), decoded.createdAt(), decoded.id(), limit + 1);

        return toPage(rows, limit, viewerId);
    }

    /** Used by FeedService, which already knows the raw (limit+1)-sized, keyset-ordered post rows. */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> toPage(List<Post> rowsWithLookahead, int limit, Long viewerId) {
        CursorPage<Post> page =
                CursorPage.of(rowsWithLookahead, limit, p -> new Cursor(p.getCreatedAt(), p.getId()).encode());
        List<PostResponse> items = enrich(page.items(), viewerId);
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    /** Batch-enriches an already-trimmed row set (author/media/likedByViewer) without re-paginating. */
    @Transactional(readOnly = true)
    public List<PostResponse> enrich(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> authorIds = posts.stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());

        Map<Long, List<Media>> mediaByPost = mediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(postIds).stream()
                .collect(Collectors.groupingBy(m -> m.getPost().getId()));
        Map<Long, UserSummary> authorsById = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));
        Set<Long> likedPostIds = new HashSet<>(likeRepository.findLikedIds(viewerId, LikeableType.POST, postIds));

        return posts.stream()
                .map(p -> toResponse(
                        p,
                        authorsById.get(p.getUser().getId()),
                        mediaByPost.getOrDefault(p.getId(), List.of()),
                        likedPostIds.contains(p.getId())))
                .toList();
    }

    private void assertVisible(Post post, User viewer) {
        if (!profileVisibilityService.isVisible(post.getUser(), viewer)) {
            throw new ForbiddenException("This account is private");
        }
    }

    /** Public so ReelService (a video-specific Post variant) can reuse the same response shape. */
    public PostResponse toResponse(Post post, UserSummary author, List<Media> media, boolean likedByViewer) {
        return new PostResponse(
                post.getId(),
                author,
                post.getCaption(),
                post.getLocation(),
                post.getType(),
                post.getMediaCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                likedByViewer,
                post.getCreatedAt(),
                media.stream().map(MediaResponse::from).toList());
    }
}
