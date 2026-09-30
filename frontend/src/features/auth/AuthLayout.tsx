import type { ReactNode } from 'react'
import { Wordmark } from '@/components/Wordmark'
import styles from './AuthLayout.module.css'

export function AuthLayout({ children, footer }: { children: ReactNode; footer?: ReactNode }) {
  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <Wordmark className={styles.logo} />
        {children}
      </div>
      {footer}
    </div>
  )
}
