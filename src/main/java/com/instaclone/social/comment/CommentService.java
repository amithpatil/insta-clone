package com.instaclone.social.comment;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.social.follow.FollowRepository;
import com.instaclone.social.follow.FollowStatus;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            FollowRepository followRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
    }

    @Transactional
    public CommentResponse addComment(Long postId, Long userId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User author = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        assertVisible(post, author);

        Comment parent = null;
        if (request.parentCommentId() != null) {
            parent = commentRepository
                    .findById(request.parentCommentId())
                    .orElseThrow(() -> new NotFoundException("Parent comment not found"));
            if (!parent.getPost().getId().equals(postId)) {
                throw new BadRequestException("Parent comment does not belong to this post");
            }
            if (parent.isReply()) {
                throw new BadRequestException("Replies can only be one level deep");
            }
        }

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setUser(author);
        comment.setParent(parent);
        comment.setText(request.text());
        comment.setCreatedAt(Instant.now());
        comment = commentRepository.save(comment);

        post.incrementCommentCount();

        return toResponse(comment, author);
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> getComments(Long postId, Long viewerId, String cursor, int limit) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        assertVisible(post, viewer);

        List<Comment> rows = cursor == null
                ? commentRepository.findFirstPageByPostId(postId, limit + 1)
                : commentRepository.findPageByPostIdAfterCursor(
                        postId, Cursor.decode(cursor).createdAt(), Cursor.decode(cursor).id(), limit + 1);

        CursorPage<Comment> page = CursorPage.of(rows, limit, c -> new Cursor(c.getCreatedAt(), c.getId()));
        List<CommentResponse> items =
                page.items().stream().map(c -> toResponse(c, c.getUser())).toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    @Transactional
    public void deleteComment(Long commentId, Long requesterId) {
        Comment comment =
                commentRepository.findById(commentId).orElseThrow(() -> new NotFoundException("Comment not found"));
        if (!comment.getUser().getId().equals(requesterId)) {
            throw new ForbiddenException("You can only delete your own comments");
        }
        commentRepository.delete(comment);
        comment.getPost().decrementCommentCount();
    }

    private void assertVisible(Post post, User viewer) {
        User author = post.getUser();
        boolean visible = !author.isPrivate()
                || author.getId().equals(viewer.getId())
                || followRepository.existsByFollowerIdAndFolloweeIdAndStatus(
                        viewer.getId(), author.getId(), FollowStatus.ACCEPTED);
        if (!visible) {
            throw new ForbiddenException("This account is private");
        }
    }

    private CommentResponse toResponse(Comment comment, User author) {
        return new CommentResponse(
                comment.getId(),
                UserSummary.from(author),
                comment.getText(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getLikeCount(),
                comment.getCreatedAt());
    }
}
