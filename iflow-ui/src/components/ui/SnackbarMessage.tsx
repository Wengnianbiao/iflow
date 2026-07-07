import { useEffect } from 'react'
import { Alert } from 'antd'
import type { MessageSeverity } from '@/components/providers/messageContext'
import styles from './SnackbarMessage.module.css'

type SnackbarMessageProps = {
  message: string
  severity?: MessageSeverity
  autoHideDuration?: number
  onDismiss?: () => void
}

export function SnackbarMessage({
  message,
  severity = 'info',
  autoHideDuration = 3000,
  onDismiss,
}: SnackbarMessageProps) {
  useEffect(() => {
    if (!onDismiss || autoHideDuration <= 0) return
    const timer = window.setTimeout(onDismiss, autoHideDuration)
    return () => window.clearTimeout(timer)
  }, [autoHideDuration, message, onDismiss])

  return (
    <div className={styles.snackbar} role="status" aria-live="polite">
      <Alert
        type={severity}
        message={message}
        showIcon
        closable={Boolean(onDismiss)}
        onClose={onDismiss}
      />
    </div>
  )
}
