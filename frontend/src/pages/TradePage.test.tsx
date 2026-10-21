import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../api/session.ts'
import type { PlayerDetail, TradeAnalysis } from '../api/types.ts'
import { TradePage } from './TradePage.tsx'

const CALEB = '11111111-1111-4111-8111-111111111111'
const GOFF = '22222222-2222-4222-8222-222222222222'
const KYLER = '33333333-3333-4333-8333-333333333333'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function profile(id: string, fullName: string, position = 'QB', team = 'CHI'): PlayerDetail {
  return { id, fullName, position, team, jerseyNumber: 1, status: 'ACTIVE', birthDate: null }
}

const profiles: Record<string, PlayerDetail> = {
  [CALEB]: profile(CALEB, 'Caleb Williams'),
  [GOFF]: profile(GOFF, 'Jared Goff', 'QB', 'DET'),
  [KYLER]: profile(KYLER, 'Kyler Murray', 'QB', 'ARI'),
}

const analysis: TradeAnalysis = {
  sideA: {
    totalValue: 3.91,
    players: [
      {
        playerId: CALEB,
        playerName: 'Caleb Williams',
        position: 'QB',
        score: 17.48,
        replacementLevel: 13.57,
        valueAboveReplacement: 3.91,
        factors: [
          { factorType: 'RECENT_PERFORMANCE', contribution: 7.73, narrative: 'Scored 14.6 PPR points in their most recent game' },
          { factorType: 'USAGE', contribution: 9.75, narrative: 'Averaging 37.5 pass attempts' },
        ],
      },
    ],
  },
  sideB: {
    totalValue: -23.49,
    players: [
      {
        playerId: GOFF,
        playerName: 'Jared Goff',
        position: 'QB',
        score: -9.92,
        replacementLevel: 13.57,
        valueAboveReplacement: -23.49,
        factors: [{ factorType: 'USAGE', contribution: -18, narrative: 'Averaging 35.5 pass attempts' }],
      },
    ],
  },
  valueDelta: 27.4,
}

function renderPage(path: string) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <TradePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TradePage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  function serve(extra: (url: string, init?: RequestInit) => Response | null = () => null) {
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input)
      const custom = extra(url, init)
      if (custom) return custom
      const id = url.split('/api/players/')[1]
      if (id && profiles[id]) return json(profiles[id])
      throw new Error(`Unexpected request: ${url}`)
    })
  }

  beforeEach(() => {
    session.set({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 })
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    session.clear()
  })

  it('needs a player on each side before it will analyze', async () => {
    serve()

    renderPage('/')

    expect(screen.getByRole('button', { name: 'Analyze trade' })).toBeDisabled()
    expect(screen.getByText('Add at least one player to each side.')).toBeInTheDocument()
  })

  it('analyzes a trade from a link and reads the result from the side you receive', async () => {
    serve((url) => (url === '/api/trades/analyze' ? json(analysis) : null))

    renderPage(`/?give=${CALEB}&get=${GOFF}`)

    expect(await screen.findByText('The side you give is worth 27.40 more points of value.')).toBeInTheDocument()
    expect(screen.getByText('You get −23.49 and give 3.91, a difference of −27.40', { exact: false })).toBeInTheDocument()
    const give = screen.getByRole('region', { name: 'You give result' })
    expect(within(give).getByText('Score 17.48 · replacement level 13.57 · value +3.91')).toBeInTheDocument()
    expect(within(give).getByText(/Recent form/)).toBeInTheDocument()
    const request = fetchMock.mock.calls.find((call) => call[0] === '/api/trades/analyze')![1]!
    expect(JSON.parse(request.body as string)).toEqual({ sideAPlayerIds: [CALEB], sideBPlayerIds: [GOFF] })
  })

  it('shows a waiting message while the analysis runs', async () => {
    serve((url) => (url === '/api/trades/analyze' ? new Promise<Response>(() => {}) as unknown as Response : null))

    renderPage(`/?give=${CALEB}&get=${GOFF}`)

    expect(await screen.findByRole('status')).toHaveTextContent('Analyzing the trade')
  })

  it('shows the server message when the analysis fails', async () => {
    serve((url) => (url === '/api/trades/analyze' ? json({ detail: 'No player found with id x' }, 404) : null))

    renderPage(`/?give=${CALEB}&get=${GOFF}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('No player found with id x')
  })

  it('hides the result once the players change, and asks for a fresh analysis', async () => {
    serve((url) => (url === '/api/trades/analyze' ? json(analysis) : null))
    renderPage(`/?give=${CALEB}&get=${GOFF}`)
    await screen.findByText('The side you give is worth 27.40 more points of value.')

    await userEvent.click(await screen.findByRole('button', { name: 'Remove Jared Goff' }))

    expect(screen.queryByText('The side you give is worth 27.40 more points of value.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Analyze trade' })).toBeDisabled()
  })

  it('adds a player from a search, to either side, without offering anyone already chosen', async () => {
    serve((url) =>
      url.startsWith('/api/players/search')
        ? json([
            { id: CALEB, fullName: 'Caleb Williams', position: 'QB', team: 'CHI' },
            { id: KYLER, fullName: 'Kyler Murray', position: 'QB', team: 'ARI' },
          ])
        : null,
    )
    renderPage(`/?give=${CALEB}`)

    const getSide = screen.getByRole('region', { name: 'You get' })
    await userEvent.click(within(getSide).getByRole('button', { name: 'Add a player' }))
    await userEvent.type(within(getSide).getByRole('searchbox'), 'ky')
    const results = await within(getSide).findByRole('list', { name: /results/ })

    expect(within(results).queryByText('Caleb Williams')).not.toBeInTheDocument()
    await userEvent.click(within(results).getByText('Kyler Murray'))

    expect(await within(getSide).findByText('Kyler Murray')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Analyze trade' })).toBeEnabled()
  })

  it('explains a player who is ruled out instead of letting -1000 speak for itself', async () => {
    const hurt: TradeAnalysis = {
      ...analysis,
      sideB: {
        totalValue: -900,
        players: [
          {
            playerId: GOFF,
            playerName: 'Jared Goff',
            position: 'QB',
            score: -990,
            replacementLevel: 13.57,
            valueAboveReplacement: -1003.57,
            factors: [{ factorType: 'INJURY', contribution: -1000, narrative: 'Listed OUT' }],
          },
        ],
      },
    }
    serve((url) => (url === '/api/trades/analyze' ? json(hurt) : null))

    renderPage(`/?give=${CALEB}&get=${GOFF}`)

    expect(await screen.findByRole('note')).toHaveTextContent('a short absence is valued as if it were a lost season')
  })
})
