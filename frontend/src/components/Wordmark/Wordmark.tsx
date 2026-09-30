import styles from './Wordmark.module.css'

/**
 * The project's own script-style wordmark — deliberately not Instagram's actual logotype/font
 * (see the Phase 5 plan's trademark note): same visual register (a casual script mark used as a
 * home link), original brand name.
 */
export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={[styles.wordmark, className].filter(Boolean).join(' ')}>Instaclone</span>
  )
}
