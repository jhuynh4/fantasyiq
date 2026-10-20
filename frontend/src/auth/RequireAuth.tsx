import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext.tsx'

export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <p className="page-status">Loading…</p>
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}
