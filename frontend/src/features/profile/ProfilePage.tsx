import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { Icon } from '@/components/Icon'
import { InfiniteGrid } from '@/components/InfiniteGrid'
import { PostGridTile } from '@/components/PostGridTile'
import * as postsApi from '@/lib/api/endpoints/posts'
import * as usersApi from '@/lib/api/endpoints/users'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ProfileHeader } from './ProfileHeader'
import styles from './ProfilePage.module.css'

export function ProfilePage() {
  const { username } = useParams<{ username: string }>()

  const { data: profile, isLoading, isError } = useQuery({
    queryKey: queryKeys.userProfile(username ?? ''),
    queryFn: () => usersApi.getProfile(username!),
    enabled: Boolean(username),
  })

  const isGated =
    Boolean(profile?.isPrivate) && profile?.viewerRelationship !== 'FOLLOWING' && profile?.viewerRelationship !== 'SELF'

  const postsQuery = useCursorInfiniteQuery(
    queryKeys.userPosts(username ?? ''),
    (cursor) => postsApi.getUserPosts(username!, cursor),
    { enabled: Boolean(username) && !isGated },
  )

  // A route like /:username that swallows any single-segment path (matching real Instagram's own
  // URL scheme, where usernames are the top-level namespace) needs its own "not found" handling —
  // without this, a typo'd or deleted username silently rendered a blank page forever instead of a
  // clear message, since the query settles into an error state that nothing was checking for.
  if (isError) {
    return <NotFoundPage />
  }

  if (isLoading || !profile) {
    return null
  }

  return (
    <div className={styles.page}>
      <ProfileHeader profile={profile} />
      <div className={styles.tabBar}>
        <div className={[styles.tab, styles.tabActive].join(' ')}>
          <Icon name="reels" size={12} />
          POSTS
        </div>
      </div>
      {isGated ? (
        <div className={styles.privateGate}>
          <Icon name="lock" size={48} className={styles.privateGateIcon} />
          <p className={styles.privateGateTitle}>This Account is Private</p>
          <p className={styles.privateGateSubtitle}>Follow this account to see their photos and videos.</p>
        </div>
      ) : (
        <div className={styles.gridWrapper}>
          <InfiniteGrid
            items={postsQuery.items}
            isLoading={postsQuery.isLoading}
            hasNextPage={postsQuery.hasNextPage}
            isFetchingNextPage={postsQuery.isFetchingNextPage}
            fetchNextPage={() => postsQuery.fetchNextPage()}
            keyFor={(post) => post.id}
            renderTile={(post) => <PostGridTile post={post} />}
            emptyState={<p>No posts yet.</p>}
          />
        </div>
      )}
    </div>
  )
}
