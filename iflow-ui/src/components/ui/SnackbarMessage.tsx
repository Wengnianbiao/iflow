import { Alert, Snackbar } from '@mui/material'
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
  return (
    <Snackbar
      className={styles.snackbar}
      open
      anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      autoHideDuration={autoHideDuration > 0 ? autoHideDuration : null}
      onClose={(_event, reason) => {
        if (reason !== 'clickaway') onDismiss?.()
      }}
    >
      <Alert
        severity={severity}
        variant="filled"
        onClose={onDismiss}
        role="status"
        aria-live="polite"
      >
        {message}
      </Alert>
    </Snackbar>
  )
}
