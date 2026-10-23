import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../api/session.ts'
import type { PlayerDetail, StartSitRecommendation } from '../api/types.ts'
import { ComparePage } from './ComparePage.tsx'

// The pickers ask the server which weeks have scores; these page tests script a fixed sequence of
// responses, so they run without that request (PeriodPickers.test.tsx covers it).
vi.mock('../features/rankings/usePeriods.ts', () => ({ useAvailablePeriods: () => ({ periods: null }) }))

const ALLEN = '11111111-1111-4111-8111-111111111111'
const JACKSON = '22222222-2222-4222-8222-222222222222'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function profile(id: string, fullName: string, position = 'QB', team = 'BUF', jersey = 17): PlayerDetail {
  return { id, fullName, position, team, jerseyNumber: jersey, status: 'ACTIVE', birthDate: null }
}

function score(playerId: string, playerName: string, factors: [string, number][], confidence: StartSitRecommendation['confidence'] = 'HIGH'): StartSitRecommendation {
  return {
    playerId,
    playerName,
    position: 'QB',
    team: 'BUF',
    score: factors.reduce((sum, [, value]) => sum + value, 0),
    confidence,
    factors: factors.map(([factorType, contribution]) => ({ factorType, contribution, narrative: '' })),
  }
}

const allenScore = score(ALLEN, 'Josh Allen', [['RECENT_PERFORMANCE', 18], ['USAGE', 10], ['MATCHUP', -7.5]])
const jacksonScore = score(JACKSON, 'Lamar Jackson', [['RECENT_PERFORMANCE', 15], ['USAGE', 8.5], ['MATCHUP', -4.25]])

function renderPage(path: string) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <ComparePage />
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

const bothPlayers = {
  [`/api/players/${ALLEN}`]: () => json(profile(ALLEN, 'Josh Allen')),
  [`/api/players/${JACKSON}`]: () => json(profile(JACKSON, 'Lamar Jackson', 'QB', 'BAL', 8)),
  '/api/recommendations/start-sit': () => json([allenScore, jacksonScore]),
}

describe('ComparePage', () => {
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

  it('shows the gap, where it comes from, and the full sums for both players', async () => {
    fetchMock.mockImplementation(routeFetch(bothPlayers))

    renderPage(`/?season=2025&week=10&a=${ALLEN}&b=${JACKSON}`)

    expect(await screen.findByText('Josh Allen has the higher model score.')).toBeInTheDocument()
    expect(screen.getByText('+1.25')).toBeInTheDocument()
    expect(screen.getByText('20.50 − 19.25 = 1.25 points. A score difference, not a lineup recommendation.')).toBeInTheDocument()
    expect(screen.getByText('Lamar Jackson gets 3.25 more points from matchup.')).toBeInTheDocument()
    expect(screen.getByText('Josh Allen: 18.00 + 10.00 − 7.50 = 20.50')).toBeInTheDocument()
    expect(screen.getByText('Lamar Jackson: 15.00 + 8.50 − 4.25 = 19.25')).toBeInTheDocument()
  })

  it('marks the higher-scoring player and leaves the other as the comparison', async () => {
    fetchMock.mockImplementation(routeFetch(bothPlayers))

    renderPage(`/?season=2025&week=10&a=${JACKSON}&b=${ALLEN}`)

    const first = await screen.findByRole('region', { name: 'First player' })
    const second = screen.getByRole('region', { name: 'Second player' })
    expect(await within(second).findByText('Higher score')).toBeInTheDocument()
    expect(within(first).queryByText('Higher score')).not.toBeInTheDocument()
  })

  it('asks the server for each position once, even for two players at it', async () => {
    fetchMock.mockImplementation(routeFetch(bothPlayers))

    renderPage(`/?season=2025&week=10&a=${ALLEN}&b=${JACKSON}`)
    await screen.findByText('Josh Allen has the higher model score.')

    const rankingCalls = fetchMock.mock.calls.filter((call) => String(call[0]).startsWith('/api/recommendations/start-sit'))
    expect(rankingCalls).toHaveLength(1)
  })

  it('warns when the two players play different positions', async () => {
    fetchMock.mockImplementation(
      routeFetch({
        [`/api/players/${ALLEN}`]: () => json(profile(ALLEN, 'Josh Allen')),
        [`/api/players/${JACKSON}`]: () => json(profile(JACKSON, 'Lamar Jackson', 'RB')),
        '/api/recommendations/start-sit?season=2025&week=10&position=QB': () => json([allenScore]),
        '/api/recommendations/start-sit?season=2025&week=10&position=RB': () => json([{ ...jacksonScore, position: 'RB' }]),
      }),
    )

    renderPage(`/?season=2025&week=10&a=${ALLEN}&b=${JACKSON}`)

    expect(await screen.findByRole('note')).toHaveTextContent('different positions')
  })

  it('lets you search for a second player at the same position', async () => {
    fetchMock.mockImplementation(
      routeFetch({
        [`/api/players/${ALLEN}`]: () => json(profile(ALLEN, 'Josh Allen')),
        '/api/recommendations/start-sit': () => json([allenScore]),
        '/api/players/search?q=jack': () =>
          json([
            { id: JACKSON, fullName: 'Lamar Jackson', position: 'QB', team: 'BAL' },
            { id: 'rb-1', fullName: 'Jackson Runner', position: 'RB', team: 'DEN' },
            { id: 'k-1', fullName: 'Jack Kicker', position: 'K', team: 'DEN' },
          ]),
      }),
    )

    renderPage(`/?season=2025&week=10&a=${ALLEN}`)
    const second = await screen.findByRole('region', { name: 'Second player' })
    await userEvent.type(within(second).getByRole('searchbox'), 'jack')

    const results = await within(second).findByRole('list', { name: 'Second player results' })
    expect(within(results).getByText('Lamar Jackson')).toBeInTheDocument()
    expect(within(results).queryByText('Jackson Runner')).not.toBeInTheDocument()
    expect(within(results).queryByText('Jack Kicker')).not.toBeInTheDocument()
    expect(within(second).getByText('Showing quarterbacks only.')).toBeInTheDocument()
  })

  it('says so when a player has no score that week', async () => {
    fetchMock.mockImplementation(
      routeFetch({
        [`/api/players/${ALLEN}`]: () => json(profile(ALLEN, 'Josh Allen')),
        [`/api/players/${JACKSON}`]: () => json(profile(JACKSON, 'Lamar Jackson', 'QB', 'BAL', 8)),
        '/api/recommendations/start-sit': () => json([allenScore]),
      }),
    )

    renderPage(`/?season=2025&week=10&a=${ALLEN}&b=${JACKSON}`)

    expect(await screen.findByText('No score for this week.')).toBeInTheDocument()
    expect(screen.getByText(/Both players need a score for this week/)).toBeInTheDocument()
  })

  it('invites you to pick players when none are chosen', async () => {
    fetchMock.mockImplementation(routeFetch({}))

    renderPage('/?season=2025&week=10')

    expect(await screen.findByText(/Search for two players at the same position/)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
