import { Modal } from '@/components/Modal'
import styles from './ActionSheet.module.css'

export interface ActionSheetAction {
  label: string
  onClick: () => void
  destructive?: boolean
}

/** Instagram's centered "..." action-sheet pattern — a list of full-width buttons in a Modal,
 * reused by post options (edit/delete/report) and profile options (block/restrict/report). */
export function ActionSheet({ onClose, actions }: { onClose: () => void; actions: ActionSheetAction[] }) {
  return (
    <Modal onClose={onClose} contentClassName={styles.content}>
      {actions.map((action) => (
        <button
          key={action.label}
          type="button"
          className={[styles.action, action.destructive ? styles.destructive : ''].join(' ')}
          onClick={() => {
            onClose()
            action.onClick()
          }}
        >
          {action.label}
        </button>
      ))}
      <button type="button" className={styles.cancel} onClick={onClose}>
        Cancel
      </button>
    </Modal>
  )
}
