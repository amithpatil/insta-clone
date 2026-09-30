import { Link, useNavigate, useParams } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Modal } from '@/components/Modal'
import * as usersApi from '@/lib/api/endpoints/users'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import { queryKeys } from '@/lib/queryKeys'
import styles from './UserListModal.module.css'

/**
 * Backs both /:username/followers and /:username/following. Rows are avatar + username + full
 * name only, no per-row Follow button — UserSummary (what these endpoints return) carries no
 * follow-state field, so a button would have nothing real to reflect without an extra lookup per row.
 */
export function UserListModal({ mode }: { mode: 'followers' | 'following' }) {
  const { username } = useParams<{ username: string }>()
  const navigate = useNavigate()

  const query = useCursorInfiniteQuery(
    mode === 'followers' ? queryKeys.followers(username ?? '') : queryKeys.following(username ?? ''),
    (cursor) =>
      mode === 'followers' ? usersApi.getFollowers(username!, cursor) : usersApi.getFollowing(username!, cursor),
    { enabled: Boolean(username) },
  )
  const sentinelRef = useInfiniteScrollSentinel(
    () => query.fetchNextPage(),
    Boolean(query.hasNextPage) && !query.isFetchingNextPage,
  )

  if (!username) return null

  return (
    <Modal onClose={() => navigate(-1)} contentClassName={styles.content} labelledBy="user-list-title">
      <h2 id="user-list-title" className={styles.title}>
        {mode === 'followers' ? 'Followers' : 'Following'}
      </h2>
      <div className={styles.list}>
        {!query.isLoading && query.items.length === 0 ? (
          <p className={styles.empty}>{mode === 'followers' ? 'No followers yet.' : 'Not following anyone yet.'}</p>
        ) : (
          query.items.map((user) => (
            <Link key={user.id} to={`/${user.username}`} className={styles.row}>
              <Avatar src={user.profilePictureUrl} alt={user.username} size={44} />
              <div className={styles.rowText}>
                <span className={styles.username}>{user.username}</span>
                <span className={styles.fullName}>{user.fullName}</span>
              </div>
            </Link>
          ))
        )}
        <div ref={sentinelRef} className={styles.sentinel} />
      </div>
    </Modal>
  )
}
