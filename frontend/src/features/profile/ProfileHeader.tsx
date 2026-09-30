import { Link, useLocation } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import type { UserProfile } from '@/lib/api/types'
import { useFollowMutation } from '@/lib/hooks/useFollowMutation'
import styles from './ProfileHeader.module.css'

export function ProfileHeader({ profile }: { profile: UserProfile }) {
  const location = useLocation()
  const { follow, unfollow } = useFollowMutation(profile.username, profile)

  return (
    <header className={styles.header}>
      <div className={styles.avatarColumn}>
        <Avatar src={profile.profilePictureUrl} alt={profile.username} size={150} className={styles.avatar} />
      </div>
      <div className={styles.info}>
        <div className={styles.topRow}>
          <h1 className={styles.username}>{profile.username}</h1>
          <div className={styles.actions}>
            {profile.viewerRelationship === 'SELF' ? (
              <>
                <Link to="/accounts/edit">
                  <Button variant="secondary">Edit profile</Button>
                </Link>
                <Link to="/accounts/edit" className={styles.settingsButton} aria-label="Settings">
                  <Icon name="more" />
                </Link>
              </>
            ) : profile.viewerRelationship === 'FOLLOWING' ? (
              <>
                <Button variant="secondary" onClick={() => unfollow.mutate()} loading={unfollow.isPending}>
                  Following
                </Button>
                <Link to="/direct/inbox">
                  <Button variant="secondary">Message</Button>
                </Link>
              </>
            ) : profile.viewerRelationship === 'REQUESTED' ? (
              <Button variant="secondary" onClick={() => unfollow.mutate()} loading={unfollow.isPending}>
                Requested
              </Button>
            ) : (
              <Button onClick={() => follow.mutate()} loading={follow.isPending}>
                Follow
              </Button>
            )}
          </div>
        </div>

        <ul className={styles.stats}>
          <li>
            <span className={styles.statCount}>{profile.postCount}</span>posts
          </li>
          <li>
            <Link to={`/${profile.username}/followers`} state={{ backgroundLocation: location }}>
              <span className={styles.statCount}>{profile.followerCount}</span>followers
            </Link>
          </li>
          <li>
            <Link to={`/${profile.username}/following`} state={{ backgroundLocation: location }}>
              <span className={styles.statCount}>{profile.followingCount}</span>following
            </Link>
          </li>
        </ul>

        {profile.fullName ? <p className={styles.fullName}>{profile.fullName}</p> : null}
        {profile.bio ? <p className={styles.bio}>{profile.bio}</p> : null}
      </div>
    </header>
  )
}
