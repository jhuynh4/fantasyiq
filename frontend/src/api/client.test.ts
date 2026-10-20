import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from './client.ts'
import { session } from './session.ts'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function tokens(suffix: string, expiresIn = 900) {
  return { accessToken: `access-${suffix}`, refreshToken: `refresh-${suffix}`, tokenType: 'Bearer', expiresIn }
}

function authorizationOf(call: unknown[]): string | null {
  return new Headers((call[1] as RequestInit).headers).get('Authorization')
}

describe('apiFetch', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    session.clear()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends the access token as a bearer token', async () => {
    session.set(tokens('1'))
    fetchMock.mockResolvedValueOnce(json({ season: 2026, week: 7 }))

    const result = await apiFetch('/api/weeks/current')

    expect(result).toEqual({ season: 2026, week: 7 })
    expect(authorizationOf(fetchMock.mock.calls[0])).toBe('Bearer access-1')
  })

  it('resolves a 204 to null instead of trying to parse a body', async () => {
    session.set(tokens('1'))
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    expect(await apiFetch('/api/weeks/current')).toBeNull()
  })

  it('refreshes once and retries when the server answers 401', async () => {
    session.set(tokens('1'))
    fetchMock
      .mockResolvedValueOnce(json({ detail: 'expired' }, 401))
      .mockResolvedValueOnce(json(tokens('2')))
      .mockResolvedValueOnce(json({ ok: true }))

    const result = await apiFetch('/api/players/1')

    expect(result).toEqual({ ok: true })
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual(['/api/players/1', '/api/auth/refresh', '/api/players/1'])
    expect(JSON.parse((fetchMock.mock.calls[1][1] as RequestInit).body as string)).toEqual({ refreshToken: 'refresh-1' })
    expect(authorizationOf(fetchMock.mock.calls[2])).toBe('Bearer access-2')
    expect(session.getRefreshToken()).toBe('refresh-2')
  })

  it('shares one refresh between concurrent requests, since a refresh token only works once', async () => {
    session.set(tokens('1'))
    fetchMock.mockImplementation(async (input, init) => {
      if (input === '/api/auth/refresh') return json(tokens('2'))
      const token = new Headers(init?.headers).get('Authorization')
      return token === 'Bearer access-2' ? json({ ok: true }) : json({ detail: 'expired' }, 401)
    })

    await Promise.all([apiFetch('/api/a'), apiFetch('/api/b'), apiFetch('/api/c')])

    const refreshCalls = fetchMock.mock.calls.filter((call) => call[0] === '/api/auth/refresh')
    expect(refreshCalls).toHaveLength(1)
  })

  it('refreshes ahead of time when the access token is about to expire', async () => {
    session.set(tokens('1', 30))
    fetchMock.mockResolvedValueOnce(json(tokens('2'))).mockResolvedValueOnce(json({ ok: true }))

    await apiFetch('/api/players/1')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual(['/api/auth/refresh', '/api/players/1'])
    expect(authorizationOf(fetchMock.mock.calls[1])).toBe('Bearer access-2')
  })

  it('signs the person out when the refresh token is rejected', async () => {
    session.set(tokens('1'))
    fetchMock
      .mockResolvedValueOnce(json({ detail: 'expired' }, 401))
      .mockResolvedValueOnce(json({ detail: 'Invalid refresh token' }, 401))

    await expect(apiFetch('/api/players/1')).rejects.toMatchObject({ status: 401 })

    expect(session.getAccessToken()).toBeNull()
    expect(session.getRefreshToken()).toBeNull()
  })

  it('keeps the session when a refresh fails for a reason other than rejection', async () => {
    session.set(tokens('1'))
    fetchMock
      .mockResolvedValueOnce(json({ detail: 'expired' }, 401))
      .mockResolvedValueOnce(json({ detail: 'down' }, 503))

    await expect(apiFetch('/api/players/1')).rejects.toBeInstanceOf(ApiError)

    expect(session.getRefreshToken()).toBe('refresh-1')
  })

  it('surfaces the problem detail from an error response', async () => {
    session.set(tokens('1'))
    fetchMock.mockResolvedValueOnce(json({ detail: 'Player 9 not found' }, 404))

    await expect(apiFetch('/api/players/9')).rejects.toMatchObject({ status: 404, message: 'Player 9 not found' })
  })

  it('reports an unreachable server as an ApiError with status 0', async () => {
    session.set(tokens('1'))
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await expect(apiFetch('/api/players/1')).rejects.toMatchObject({ status: 0 })
  })
})
