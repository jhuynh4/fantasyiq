import { createContext, useCallback, useContext, useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { refreshSession } from '../api/client.ts'
import * as authApi from '../api/auth.ts'
import { decodeClaims } from '../api/jwt.ts'
import { session } from '../api/session.ts'

type Status = 'loading' | 'authenticated' | 'anonymous'

interface AuthState {
  status: Status
  email: string | null
  role: string | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [restoring, setRestoring] = useState(() => session.getRefreshToken() !== null)
  const accessToken = useSyncExternalStore(session.subscribe, session.getAccessToken)

  // A saved refresh token means someone was signed in before the reload; trade it for a fresh
  // access token before deciding which screen to show.
  useEffect(() => {
    if (!restoring) return
    refreshSession()
      .catch(() => {})
      .finally(() => setRestoring(false))
  }, [restoring])

  const login = useCallback(async (email: string, password: string) => {
    await authApi.login(email, password)
  }, [])

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    await authApi.register(email, password, displayName)
  }, [])

  const logout = useCallback(() => {
    authApi.logout()
    queryClient.clear()
  }, [queryClient])

  const value = useMemo<AuthState>(() => {
    const claims = decodeClaims(accessToken)
    const status: Status = restoring ? 'loading' : accessToken ? 'authenticated' : 'anonymous'
    return { status, email: claims.email, role: claims.role, login, register, logout }
  }, [accessToken, restoring, login, register, logout])

  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside an AuthProvider')
  return context
}
