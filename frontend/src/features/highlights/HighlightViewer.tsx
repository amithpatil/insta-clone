import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Icon } from '@/components/Icon'
import * as highlightsApi from '@/lib/api/endpoints/highlights'
import { queryKeys } from '@/lib/queryKeys'
import styles from './HighlightViewer.module.css'

/** Fullscreen highlight playback — same visual chrome as StoryViewer (progress segments, tap
 * zones, dark frame) adapted for a highlight's permanent items instead of a live story group. */
export function HighlightViewer({ highlightId, onClose }: { highlightId: number; onClose: () => void }) {
  const [index, setIndex] = useState(0)
  const { data: detail } = useQuery({
    queryKey: queryKeys.highlightDetail(highlightId),
    queryFn: () => highlightsApi.getHighlightDetail(highlightId),
  })

  if (!detail || detail.items.length === 0) return null
  const item = detail.items[index]

  function goToNext() {
    if (!detail) return
    if (index < detail.items.length - 1) {
      setIndex(index + 1)
    } else {
      onClose()
    }
  }

  function goToPrevious() {
    if (index > 0) setIndex(index - 1)
  }

  return (
    <div className={styles.overlay}>
      <div className={styles.frame}>
        <div className={styles.progressRow}>
          {detail.items.map((i, pos) => (
            <div key={i.id} className={styles.segment}>
              <div
                className={[styles.segmentFill, pos <= index ? styles.segmentFillDone : ''].join(' ')}
              />
            </div>
          ))}
        </div>
        <header className={styles.header}>
          <span className={styles.title}>{detail.title}</span>
          <button type="button" className={styles.iconButton} onClick={onClose} aria-label="Close">
            <Icon name="close" />
          </button>
        </header>
        <img className={styles.media} src={item.mediaUrl} alt={detail.title} />
        <button type="button" className={[styles.navZone, styles.navZoneLeft].join(' ')} onClick={goToPrevious} aria-label="Previous" />
        <button type="button" className={[styles.navZone, styles.navZoneRight].join(' ')} onClick={goToNext} aria-label="Next" />
      </div>
    </div>
  )
}
