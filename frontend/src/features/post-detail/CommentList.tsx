import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import * as commentsApi from '@/lib/api/endpoints/comments'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import { queryKeys } from '@/lib/queryKeys'
import styles from './CommentList.module.css'

export function CommentList({ postId }: { postId: number }) {
  const { items, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useCursorInfiniteQuery(
    queryKeys.comments(postId),
    (cursor) => commentsApi.getComments(postId, cursor),
  )
  const sentinelRef = useInfiniteScrollSentinel(() => fetchNextPage(), Boolean(hasNextPage) && !isFetchingNextPage)

  if (isLoading) {
    return <div className={styles.list} />
  }

  if (items.length === 0) {
    return (
      <div className={styles.list}>
        <p className={styles.empty}>No comments yet.</p>
      </div>
    )
  }

  return (
    <div className={styles.list}>
      {items.map((comment) => (
        <div key={comment.id} className={styles.row}>
          <Link to={`/${comment.author.username}`}>
            <Avatar src={comment.author.profilePictureUrl} alt={comment.author.username} size={24} />
          </Link>
          <div>
            <p className={styles.text}>
              <Link to={`/${comment.author.username}`} className={styles.username}>
                {comment.author.username}
              </Link>
              {comment.text}
            </p>
            <div className={styles.meta}>
              <span className={styles.timestamp}>{formatRelativeTime(comment.createdAt)}</span>
              {comment.likeCount > 0 ? (
                <span className={styles.likeCount}>
                  {comment.likeCount} {comment.likeCount === 1 ? 'like' : 'likes'}
                </span>
              ) : null}
            </div>
          </div>
        </div>
      ))}
      <div ref={sentinelRef} className={styles.sentinel} />
    </div>
  )
}
