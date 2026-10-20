import type { AuthResponse } from './types.ts'

const REFRESH_KEY = 'fiq.refreshToken'

// The access token lives only in memory, so a script injected into the page can't read it back
// from storage later. The refresh token has to survive a reload to keep someone signed in, so it
// is the one thing persisted.
let accessToken: string | null = null
let expiresAt = 0
const listeners = new Set<() => void>()

function readRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY)
  } catch {
    return null
  }
}

function writeRefreshToken(token: string | null) {
  try {
    if (token === null) localStorage.removeItem(REFRESH_KEY)
    else localStorage.setItem(REFRESH_KEY, token)
  } catch {
    // Storage can be unavailable (private windows, blocked site data); the session then simply
    // lasts until the page is closed.
  }
}

export const session = {
  getAccessToken: () => accessToken,
  getRefreshToken: readRefreshToken,
  /** Milliseconds until the access token expires; 0 or less means it already has. */
  accessTokenTtl: () => (accessToken === null ? 0 : expiresAt - Date.now()),

  set(tokens: AuthResponse) {
    accessToken = tokens.accessToken
    expiresAt = Date.now() + tokens.expiresIn * 1000
    writeRefreshToken(tokens.refreshToken)
    listeners.forEach((listener) => listener())
  },

  clear() {
    accessToken = null
    expiresAt = 0
    writeRefreshToken(null)
    listeners.forEach((listener) => listener())
  },

  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}
