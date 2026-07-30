import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'

import { AppQueryProvider } from '@/auth/query-client'

import App from './App.tsx'
import './i18n'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppQueryProvider>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </AppQueryProvider>
  </StrictMode>,
)
