import { session } from './session.ts'
import type { ApiProblem, AuthResponse } from './types.ts'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/** Refresh a little before expiry so a request doesn't leave with a token that dies in flight. */
const REFRESH_MARGIN_MS = 60_000

async function toApiError(response: Response): Promise<ApiError> {
  let detail = ''
  try {
    const problem = (await response.json()) as ApiProblem
    detail = problem.detail ?? ''
  } catch {
    // Not a problem-detail body (e.g. a proxy error page); fall back to the status text.
  }
  return new ApiError(response.status, detail || response.statusText || `Request failed (${response.status})`)
}

/** POST without any token handling; used by sign-in, register and the refresh itself. */
export async function postJson<T>(path: string, body: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Could not reach the server. Check your connection and try again.')
  }
  if (!response.ok) throw await toApiError(response)
  return (await response.json()) as T
}

let refreshInFlight: Promise<void> | null = null

/**
 * Refresh tokens are single-use (each refresh revokes the old one), so two requests that both
 * notice an expired token must share one refresh call; a second one would present an
 * already-revoked token and sign the person out.
 */
export function refreshSession(): Promise<void> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function doRefresh(): Promise<void> {
  const refreshToken = session.getRefreshToken()
  if (!refreshToken) throw new ApiError(401, 'You are signed out.')
  try {
    session.set(await postJson<AuthResponse>('/api/auth/refresh', { refreshToken }))
  } catch (error) {
    // Only a definite rejection ends the session; a dropped connection or a 5xx says nothing
    // about whether the refresh token is still good, and the next attempt may well succeed.
    if (error instanceof ApiError && error.status === 401) session.clear()
    throw error
  }
}

function send(path: string, init: RequestInit | undefined): Promise<Response> {
  const headers = new Headers(init?.headers)
  const token = session.getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(path, { ...init, headers })
}

/** Authenticated request; resolves to null for a 204, throws ApiError for any other failure. */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T | null> {
  if (session.getRefreshToken() && session.accessTokenTtl() < REFRESH_MARGIN_MS) {
    await refreshSession()
  }

  let response: Response
  try {
    response = await send(path, init)
    if (response.status === 401 && session.getRefreshToken()) {
      await refreshSession()
      response = await send(path, init)
    }
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(0, 'Could not reach the server. Check your connection and try again.')
  }

  if (response.status === 204) return null
  if (!response.ok) throw await toApiError(response)
  return (await response.json()) as T
}
