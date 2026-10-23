import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../api/session.ts'
import { PeriodPickers } from './PeriodPickers.tsx'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderPickers(season: number, week: number, onChange = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PeriodPickers season={season} week={week} onChange={onChange} />
    </QueryClientProvider>,
  )
  return onChange
}

function optionsOf(label: string): string[] {
  const select = screen.getByRole('combobox', { name: label })
  return within(select)
    .getAllByRole('option')
    .map((option) => option.textContent ?? '')
}

describe('PeriodPickers', () => {
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

  it('offers only the weeks that have scores', async () => {
    fetchMock.mockImplementation(async () =>
      json([
        { season: 2026, weeks: [7] },
        { season: 2025, weeks: [5, 6, 7, 18] },
      ]),
    )

    renderPickers(2025, 6)

    await vi.waitFor(() => expect(optionsOf('Week')).toEqual(['Week 5', 'Week 6', 'Week 7', 'Week 18']))
    expect(optionsOf('Season')).toEqual(['Season 2026', 'Season 2025'])
    expect(fetchMock.mock.calls[0][0]).toBe('/api/recommendations/periods')
  })

  it('keeps the week on show selectable and says it has no scores yet', async () => {
    fetchMock.mockImplementation(async () =>
      json([
        { season: 2026, weeks: [] },
        { season: 2025, weeks: [5, 6] },
      ]),
    )

    renderPickers(2026, 7)

    await vi.waitFor(() => expect(optionsOf('Week')).toEqual(['Week 7 · no scores yet']))
  })

  it('moves to a scored week when the season changes to one without the current week', async () => {
    fetchMock.mockImplementation(async () =>
      json([
        { season: 2026, weeks: [7] },
        { season: 2025, weeks: [5, 6, 18] },
      ]),
    )
    const onChange = renderPickers(2026, 7)
    await vi.waitFor(() => expect(optionsOf('Season')).toEqual(['Season 2026', 'Season 2025']))

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Season' }), '2025')

    expect(onChange).toHaveBeenCalledWith({ season: 2025, week: 18 })
  })

  it('reports a week change together with the season', async () => {
    fetchMock.mockImplementation(async () => json([{ season: 2025, weeks: [5, 6] }]))
    const onChange = renderPickers(2025, 5)
    await vi.waitFor(() => expect(optionsOf('Week')).toEqual(['Week 5', 'Week 6']))

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Week' }), '6')

    expect(onChange).toHaveBeenCalledWith({ season: 2025, week: 6 })
  })

  it('offers every week rather than breaking when the request fails', async () => {
    fetchMock.mockImplementation(async () => {
      throw new TypeError('Failed to fetch')
    })

    renderPickers(2025, 10)

    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
    expect(optionsOf('Week')).toHaveLength(18)
  })
})
