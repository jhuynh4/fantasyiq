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
        factors: [{ factorType: 'BASELINE', contribution: 17.48, narrative: 'Averaging 17.5 PPR points over 8 games this season' }],
      },
    ],
  },
  sideB: {
    totalValue: -3.65,
    players: [
      {
        playerId: GOFF,
        playerName: 'Jared Goff',
        position: 'QB',
        score: 9.92,
        replacementLevel: 13.57,
        valueAboveReplacement: -3.65,
        factors: [{ factorType: 'BASELINE', contribution: 9.92, narrative: 'Averaging 9.9 PPR points over 8 games this season' }],
      },
    ],
  },
  valueDelta: 7.56,
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

    expect(await screen.findByText('The side you give is worth 7.56 more points of value.')).toBeInTheDocument()
    expect(screen.getByText('You get −3.65 and give 3.91, a difference of −7.56', { exact: false })).toBeInTheDocument()
    const give = screen.getByRole('region', { name: 'You give result' })
    expect(within(give).getByText('Score 17.48 · replacement level 13.57 · value +3.91')).toBeInTheDocument()
    expect(within(give).getByText(/Season average/)).toBeInTheDocument()
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
    await screen.findByText('The side you give is worth 7.56 more points of value.')

    await userEvent.click(await screen.findByRole('button', { name: 'Remove Jared Goff' }))

    expect(screen.queryByText('The side you give is worth 7.56 more points of value.')).not.toBeInTheDocument()
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

  it('shows an injury as an ordinary discount, with no warning about a lost season', async () => {
    const hurt: TradeAnalysis = {
      ...analysis,
      sideB: {
        totalValue: -5.15,
        players: [
          {
            playerId: GOFF,
            playerName: 'Jared Goff',
            position: 'QB',
            score: 8.42,
            replacementLevel: 13.57,
            valueAboveReplacement: -5.15,
            factors: [
              { factorType: 'BASELINE', contribution: 9.92, narrative: 'Averaging 9.9 PPR points over 8 games this season' },
              { factorType: 'INJURY', contribution: -1.5, narrative: 'Listed as OUT -- assumed to miss a game or two' },
            ],
          },
        ],
      },
    }
    serve((url) => (url === '/api/trades/analyze' ? json(hurt) : null))

    renderPage(`/?give=${CALEB}&get=${GOFF}`)

    const get = await screen.findByRole('region', { name: 'You get result' })
    expect(within(get).getByText(/Injury status/)).toHaveTextContent('−1.50')
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
  })
})
