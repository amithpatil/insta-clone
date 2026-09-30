import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { CaptionText } from '@/components/CaptionText'
import { Icon } from '@/components/Icon'
import * as commentsApi from '@/lib/api/endpoints/comments'
import * as postsApi from '@/lib/api/endpoints/posts'
import { formatCount, formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useLikeMutation } from '@/lib/hooks/useLikeMutation'
import { patchPostInAllCaches } from '@/lib/queryHelpers'
import { queryKeys } from '@/lib/queryKeys'
import { CommentList } from './CommentList'
import styles from './PostDetail.module.css'

export function PostDetail({ postId }: { postId: number }) {
  const { data: post, isLoading } = useQuery({
    queryKey: queryKeys.post(postId),
    queryFn: () => postsApi.getPost(postId),
  })
  const [commentText, setCommentText] = useState('')
  const [saved, setSaved] = useState(false)
  const queryClient = useQueryClient()

  const likeMutation = useLikeMutation(postId, post?.likedByViewer ?? false, post?.likeCount ?? 0)
  const commentMutation = useMutation({
    mutationFn: (text: string) => commentsApi.createComment(postId, { text }),
    onSuccess: () => {
      if (post) patchPostInAllCaches(queryClient, postId, { commentCount: post.commentCount + 1 })
      queryClient.invalidateQueries({ queryKey: queryKeys.comments(postId) })
      setCommentText('')
    },
  })

  if (isLoading || !post) {
    return <div className={styles.layout} />
  }

  const primaryMedia = post.media[0]

  function handleSubmitComment() {
    const text = commentText.trim()
    if (!text) return
    commentMutation.mutate(text)
  }

  return (
    <div className={styles.layout}>
      <div className={styles.media}>
        {primaryMedia ? (
          primaryMedia.mediaType === 'VIDEO' ? (
            <video src={primaryMedia.url} poster={primaryMedia.thumbnailUrl ?? undefined} controls autoPlay muted />
          ) : (
            <img src={primaryMedia.url} alt={post.caption || `Post by ${post.author.username}`} />
          )
        ) : null}
      </div>
      <div className={styles.side}>
        <header className={styles.header}>
          <Link to={`/${post.author.username}`}>
            <Avatar src={post.author.profilePictureUrl} alt={post.author.username} size={32} />
          </Link>
          <Link to={`/${post.author.username}`} className={styles.username}>
            {post.author.username}
          </Link>
          <button type="button" aria-label="More options">
            <Icon name="options" />
          </button>
        </header>

        {post.caption ? (
          <div className={styles.captionRow}>
            <Avatar src={post.author.profilePictureUrl} alt={post.author.username} size={32} />
            <CaptionText
              username={post.author.username}
              caption={post.caption}
              className={styles.captionText}
              usernameClassName={styles.captionUsername}
              hashtagClassName={styles.hashtag}
            />
          </div>
        ) : null}

        <CommentList postId={postId} />

        <div className={styles.footer}>
          <div className={styles.actions}>
            <button
              type="button"
              className={[styles.actionButton, post.likedByViewer ? styles.liked : ''].join(' ')}
              onClick={() => likeMutation.mutate()}
              aria-pressed={post.likedByViewer}
              aria-label={post.likedByViewer ? 'Unlike' : 'Like'}
            >
              <Icon name="heart" variant={post.likedByViewer ? 'filled' : 'outline'} />
            </button>
            <button type="button" className={styles.actionButton} aria-label="Comment">
              <Icon name="comment" />
            </button>
            <button type="button" className={styles.actionButton} aria-label="Share">
              <Icon name="share" />
            </button>
            <button
              type="button"
              className={[styles.actionButton, styles.bookmark].join(' ')}
              onClick={() => setSaved((s) => !s)}
              aria-pressed={saved}
              aria-label={saved ? 'Remove from saved' : 'Save'}
            >
              <Icon name="bookmark" variant={saved ? 'filled' : 'outline'} />
            </button>
          </div>
          <p className={styles.likeCount}>
            {formatCount(post.likeCount)} {post.likeCount === 1 ? 'like' : 'likes'}
          </p>
          <time className={styles.timestamp} dateTime={post.createdAt}>
            {formatRelativeTime(post.createdAt)}
          </time>
          <div className={styles.composer}>
            <input
              className={styles.composerInput}
              placeholder="Add a comment…"
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSubmitComment()
              }}
              maxLength={2200}
            />
            <button
              type="button"
              className={styles.postButton}
              disabled={!commentText.trim() || commentMutation.isPending}
              onClick={handleSubmitComment}
            >
              Post
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
