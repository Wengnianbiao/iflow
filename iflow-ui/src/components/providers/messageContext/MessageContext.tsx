import { createContext, useContext } from 'react'

export type MessageSeverity = 'success' | 'info' | 'warning' | 'error'

export type PopoverMessage = {
  text: string
  severity: MessageSeverity
}

type MessageContextValue = {
  setMessage: (message: PopoverMessage | null) => void
}

export const MessageContext = createContext<MessageContextValue>({
  setMessage: () => undefined,
})

export function useMessageContext() {
  return useContext(MessageContext)
}
