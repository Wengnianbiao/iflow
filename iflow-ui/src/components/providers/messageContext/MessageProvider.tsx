import { useState, type ReactNode } from 'react'
import { SnackbarMessage } from '@/components/ui/SnackbarMessage'
import {
  MessageContext,
  type PopoverMessage,
} from './MessageContext'

type MessageProviderProps = {
  children: ReactNode
}

export function MessageProvider({ children }: MessageProviderProps) {
  const [message, setMessage] = useState<PopoverMessage | null>(null)

  return (
    <>
      {message ? (
        <SnackbarMessage
          message={message.text}
          severity={message.severity}
          onDismiss={() => setMessage(null)}
        />
      ) : null}
      <MessageContext.Provider value={{ setMessage }}>
        {children}
      </MessageContext.Provider>
    </>
  )
}
