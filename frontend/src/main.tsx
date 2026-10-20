import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '@fontsource/inter/400.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/600.css'
import './styles/tokens.css'
import './styles/global.css'
import './styles/app.css'
import './styles/rankings.css'
import './styles/player.css'
import './styles/compare.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthContext.tsx'
import { ThemeProvider } from './theme/ThemeContext.tsx'

const queryClient = new QueryClient({
  defaultOptions: {
    // A failed request is shown to the person, who can retry; silent retries would just delay that.
    queries: { retry: false, refetchOnWindowFocus: false },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
)
