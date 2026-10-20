import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../api/session.ts'
import type { PlayerDetail, StartSitRecommendation } from '../api/types.ts'
import { PlayerPage } from './PlayerPage.tsx'

const ID = '7d7c5a73-819a-4a99-ade8-c3fd7cb9c664'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const player: PlayerDetail = {
  id: ID,
  fullName: 'Caleb Williams',
  position: 'QB',
  team: 'CHI',
  jerseyNumber: 18,
  status: 'ACTIVE',
  birthDate: '2001-11-18',
}

function recommendation(overrides: Partial<StartSitRecommendation>): StartSitRecommendation {
  return {
    playerId: ID,
    playerName: 'Caleb Williams',
    position: 'QB',
    team: 'CHI',
    score: 41.97,
    confidence: 'MEDIUM',
    factors: [
      { factorType: 'MATCHUP', contribution: 14.11, narrative: 'Opponent has allowed the #19 ranked fantasy production to QB' },
      { factorType: 'USAGE', contribution: 12.75, narrative: 'Averaging 36.0 pass attempts over the last 2 games' },
      { factorType: 'RECENT_PERFORMANCE', contribution: 15.11, narrative: 'Scored 28.5 PPR points in their most recent game' },
    ],
    ...overrides,
  }
}

function renderPage(path: string) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/players/:id" element={<PlayerPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function routeFetch(handlers: Record<string, () => Response>) {
  return async (input: RequestInfo | URL) => {
    const url = String(input)
    const match = Object.keys(handlers).find((prefix) => url.startsWith(prefix))
    if (!match) throw new Error(`Unexpected request: ${url}`)
    return handlers[match]()
  }
}

describe('PlayerPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    session.set({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 })
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    session.clear()
  })

  it('shows the score, the rank in the position, each factor with its explanation, and the arithmetic', async () => {
    const other = recommendation({ playerId: 'other-id', playerName: 'Someone Else', score: 50 })
    fetchMock.mockImplementation(
      routeFetch({
        [`/api/players/${ID}`]: () => json(player),
        '/api/recommendations/start-sit': () => json([other, recommendation({})]),
      }),
    )

    renderPage(`/players/${ID}?season=2025&week=10`)

    expect(await screen.findByRole('heading', { level: 1, name: 'Caleb Williams' })).toBeInTheDocument()
    expect(screen.getByText('QB · CHI · #18')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(await screen.findByText('#2 QB')).toBeInTheDocument()
    expect(screen.getByText('Opponent has allowed the #19 ranked fantasy production to QB')).toBeInTheDocument()
    expect(screen.getByText('+14.11')).toBeInTheDocument()
    expect(screen.getByText('15.11 + 12.75 + 14.11 = 41.97')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Quarterback rankings/ })).toHaveAttribute(
      'href',
      '/?season=2025&week=10&position=QB',
    )
  })

  it('asks for the rankings of the position the player plays, for the week in the address', async () => {
    fetchMock.mockImplementation(
      routeFetch({
        [`/api/players/${ID}`]: () => json(player),
        '/api/recommendations/start-sit': () => json([recommendation({})]),
      }),
    )

    renderPage(`/players/${ID}?season=2025&week=10`)
    await screen.findByText('#1 QB')

    expect(fetchMock.mock.calls.map((call) => call[0])).toContain(
      '/api/recommendations/start-sit?season=2025&week=10&position=QB',
    )
  })

  it('says so when the player has no score that week, and still shows who they are', async () => {
    fetchMock.mockImplementation(
      routeFetch({
        [`/api/players/${ID}`]: () => json(player),
        '/api/recommendations/start-sit': () => json([]),
      }),
    )

    renderPage(`/players/${ID}?season=2025&week=3`)

    expect(await screen.findByText(/has no score for season 2025, week 3/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Caleb Williams' })).toBeInTheDocument()
  })

  it('shows a player who is ruled out as out, with the injury penalty in the sum', async () => {
    const hurt = recommendation({
      score: -988.9,
      confidence: 'LOW',
      factors: [
        { factorType: 'INJURY', contribution: -1000, narrative: 'Listed OUT' },
        { factorType: 'RECENT_PERFORMANCE', contribution: 11.1, narrative: 'Scored 20.9 PPR points in their most recent game' },
      ],
    })
    fetchMock.mockImplementation(
      routeFetch({
        [`/api/players/${ID}`]: () => json(player),
        '/api/recommendations/start-sit': () => json([hurt]),
      }),
    )

    renderPage(`/players/${ID}?season=2025&week=10`)

    expect(await screen.findByText('Out')).toBeInTheDocument()
    expect(screen.getByText('Ruled out')).toBeInTheDocument()
    expect(screen.getByText('11.10 − 1000.00 = −988.90')).toBeInTheDocument()
  })

  it('shows a not-found message for an unknown player', async () => {
    fetchMock.mockImplementation(routeFetch({ [`/api/players/${ID}`]: () => json({ detail: 'Player not found' }, 404) }))

    renderPage(`/players/${ID}?season=2025&week=10`)

    expect(await screen.findByRole('alert')).toHaveTextContent("couldn't find that player")
  })

  it('does not call the server for an address that is not a player id', async () => {
    renderPage('/players/not-a-uuid?season=2025&week=10')

    expect(await screen.findByRole('alert')).toHaveTextContent("couldn't find that player")
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
