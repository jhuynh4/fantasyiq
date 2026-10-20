import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../api/session.ts'
import type { StartSitRecommendation } from '../api/types.ts'
import { RankingsPage } from './RankingsPage.tsx'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function recommendation(overrides: Partial<StartSitRecommendation>): StartSitRecommendation {
  return {
    playerId: crypto.randomUUID(),
    playerName: 'Josh Allen',
    position: 'QB',
    team: 'BUF',
    score: 33.59,
    confidence: 'HIGH',
    factors: [
      { factorType: 'RECENT_PERFORMANCE', contribution: 15.9, narrative: '' },
      { factorType: 'WEATHER', contribution: -2.1, narrative: '' },
    ],
    ...overrides,
  }
}

function renderPage(path: string) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <RankingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RankingsPage', () => {
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

  it('numbers the players in the order the server returned them', async () => {
    fetchMock.mockResolvedValueOnce(
      json([
        recommendation({ playerName: 'Josh Allen', score: 33.59 }),
        recommendation({ playerName: 'Lamar Jackson', score: 30.85, team: 'BAL', confidence: 'MEDIUM' }),
      ]),
    )

    renderPage('/?season=2025&week=10&position=QB')

    const rows = await screen.findAllByRole('row')
    const body = rows.slice(1)
    expect(within(body[0]).getByText('Josh Allen')).toBeInTheDocument()
    expect(within(body[0]).getByText('01')).toBeInTheDocument()
    expect(within(body[1]).getByText('02')).toBeInTheDocument()
    expect(within(body[1]).getByText('30.85')).toBeInTheDocument()
    expect(screen.getByText('Quarterbacks · 2 players')).toBeInTheDocument()
    expect(fetchMock.mock.calls[0][0]).toBe('/api/recommendations/start-sit?season=2025&week=10&position=QB')
  })

  it('asks for the position that is picked', async () => {
    fetchMock.mockImplementation(async () => json([]))
    renderPage('/?season=2025&week=10&position=QB')
    await screen.findByText(/No rankings have been generated/)

    await userEvent.click(screen.getByRole('tab', { name: 'WR' }))

    expect(await screen.findByText(/No rankings have been generated for wide receivers/)).toBeInTheDocument()
    expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/recommendations/start-sit?season=2025&week=10&position=WR')
  })

  it('starts from the current week when the address names none', async () => {
    fetchMock.mockResolvedValueOnce(json({ season: 2026, week: 7 })).mockResolvedValueOnce(json([recommendation({})]))

    renderPage('/')

    expect(await screen.findByText('Josh Allen')).toBeInTheDocument()
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/weeks/current',
      '/api/recommendations/start-sit?season=2026&week=7&position=QB',
    ])
  })

  it('falls back to the end of the latest season when there is no current week', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(json([]))

    renderPage('/')

    await screen.findByText(/No rankings have been generated/)
    expect(fetchMock.mock.calls[1][0]).toMatch(/week=18&position=QB$/)
  })

  it('shows the top 50 and the rest on request, keeping the numbering', async () => {
    const many = Array.from({ length: 60 }, (_, index) =>
      recommendation({ playerName: `Player ${index + 1}`, score: 100 - index }),
    )
    fetchMock.mockResolvedValueOnce(json(many))

    renderPage('/?season=2025&week=10')

    await screen.findByText('Player 1')
    expect(screen.getAllByRole('row')).toHaveLength(51)
    expect(screen.queryByText('Player 51')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Show all 60 players' }))

    expect(screen.getByText('Player 60')).toBeInTheDocument()
    expect(screen.getByText('60')).toBeInTheDocument()
  })

  it('shows the server message when the rankings cannot be loaded', async () => {
    fetchMock.mockResolvedValueOnce(json({ detail: 'Something broke' }, 500))

    renderPage('/?season=2025&week=10')

    expect(await screen.findByRole('alert')).toHaveTextContent('Something broke')
  })

  it('describes each row\'s factor shape for screen readers, including a player ruled out', async () => {
    fetchMock.mockResolvedValueOnce(
      json([
        recommendation({
          playerName: 'Hurt Player',
          factors: [{ factorType: 'INJURY', contribution: -1000, narrative: '' }],
        }),
      ]),
    )

    renderPage('/?season=2025&week=10')

    expect(await screen.findByRole('img', { name: 'Factor contributions: Injury status: out' })).toBeInTheDocument()
  })
})
