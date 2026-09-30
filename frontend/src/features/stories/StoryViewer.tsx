import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { useAuth } from '@/contexts/useAuth'
import * as storiesApi from '@/lib/api/endpoints/stories'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { queryKeys } from '@/lib/queryKeys'
import type { AuthorStories } from './useStoriesFeed'
import styles from './StoryViewer.module.css'

export interface StoryViewerProps {
  groups: AuthorStories[]
  initialGroupIndex: number
  onClose: () => void
}

export function StoryViewer({ groups, initialGroupIndex, onClose }: StoryViewerProps) {
  const [groupIndex, setGroupIndex] = useState(initialGroupIndex)
  const [storyIndex, setStoryIndex] = useState(0)
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const group = groups[groupIndex]
  const story = group?.stories[storyIndex]

  const deleteMutation = useMutation({
    mutationFn: (id: number) => storiesApi.deleteStory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.storiesFeed() })
      if (user) queryClient.invalidateQueries({ queryKey: queryKeys.userStories(user.username) })
      onClose()
    },
  })

  function goToNextStory() {
    if (!group) return
    if (storyIndex < group.stories.length - 1) {
      setStoryIndex(storyIndex + 1)
    } else if (groupIndex < groups.length - 1) {
      setGroupIndex(groupIndex + 1)
      setStoryIndex(0)
    } else {
      onClose()
    }
  }

  function goToPreviousStory() {
    if (storyIndex > 0) {
      setStoryIndex(storyIndex - 1)
    } else if (groupIndex > 0) {
      setGroupIndex(groupIndex - 1)
      setStoryIndex(0)
    }
  }

  if (!group || !story) return null

  const isOwn = story.author.id === user?.id

  return (
    <div className={styles.overlay}>
      <div className={styles.frame}>
        <div className={styles.progressRow}>
          {group.stories.map((s, i) => (
            <div key={s.id} className={styles.segment}>
              <div
                className={[
                  styles.segmentFill,
                  i === storyIndex ? styles.segmentFillActive : '',
                  i < storyIndex ? styles.segmentFillDone : '',
                ].join(' ')}
                onAnimationEnd={i === storyIndex ? goToNextStory : undefined}
              />
            </div>
          ))}
        </div>
        <header className={styles.header}>
          <Avatar src={story.author.profilePictureUrl} alt={story.author.username} size={32} />
          <span className={styles.username}>{story.author.username}</span>
          <span className={styles.timestamp}>{formatRelativeTime(story.createdAt)}</span>
          <div className={styles.headerActions}>
            {isOwn ? (
              <button type="button" className={styles.iconButton} onClick={() => deleteMutation.mutate(story.id)} aria-label="Delete story">
                <Icon name="trash" />
              </button>
            ) : null}
            <button type="button" className={styles.iconButton} onClick={onClose} aria-label="Close">
              <Icon name="close" />
            </button>
          </div>
        </header>
        <img className={styles.media} src={story.mediaUrl} alt={`${story.author.username}'s story`} />
        <button type="button" className={[styles.navZone, styles.navZoneLeft].join(' ')} onClick={goToPreviousStory} aria-label="Previous story" />
        <button type="button" className={[styles.navZone, styles.navZoneRight].join(' ')} onClick={goToNextStory} aria-label="Next story" />
      </div>
    </div>
  )
}
