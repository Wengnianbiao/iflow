import './monaco'
import React from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { AppQueryProvider } from '@/components/providers/AppQueryProvider'
import { MessageProvider } from '@/components/providers/messageContext'
import { ThemeProvider } from '@/theme'
import { router } from '@/routes/router'
import './styles.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AppQueryProvider>
        <MessageProvider>
          <RouterProvider router={router} />
        </MessageProvider>
      </AppQueryProvider>
    </ThemeProvider>
  </React.StrictMode>,
)
