import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from './App.tsx'
import { IdentityProvider } from './identity/IdentityProvider.tsx'
import './index.css'

// One QueryClient for the whole app. Polling is configured per query (refetchInterval), and failed
// requests are not retried automatically: for a delivery tracker a quick, visible error is better
// than silently waiting through three retries.
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <IdentityProvider>
          <App />
        </IdentityProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
