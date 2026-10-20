import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth.tsx'
import { AppShell } from './components/AppShell.tsx'
import { ComparePage } from './pages/ComparePage.tsx'
import { LoginPage } from './pages/LoginPage.tsx'
import { RankingsPage } from './pages/RankingsPage.tsx'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<RankingsPage />} />
          <Route path="compare" element={<ComparePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
