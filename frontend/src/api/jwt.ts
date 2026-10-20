export interface TokenClaims {
  email: string | null
  role: string | null
}

/**
 * Reads the display-only claims from an access token. Nothing here is trusted: the server
 * checks the signature on every request, and this only decides what to show (an initials badge).
 */
export function decodeClaims(token: string | null): TokenClaims {
  const empty = { email: null, role: null }
  if (!token) return empty
  try {
    const payload = token.split('.')[1]
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)))
    const claims = JSON.parse(json) as { email?: unknown; role?: unknown }
    return {
      email: typeof claims.email === 'string' ? claims.email : null,
      role: typeof claims.role === 'string' ? claims.role : null,
    }
  } catch {
    return empty
  }
}
