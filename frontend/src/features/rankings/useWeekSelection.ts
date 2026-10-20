import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../api/client.ts'
import type { CurrentWeek } from '../../api/types.ts'
import { fallbackWeek, parseInteger } from './period.ts'

export interface WeekSelection {
  season: number | null
  week: number | null
  /** True while the default week is still being asked for. */
  resolving: boolean
  error: Error | null
}

/**
 * The season and week a screen shows: the ones in the address if it has both, otherwise the
 * server's current week, otherwise the end of the latest season.
 */
export function useWeekSelection(params: URLSearchParams): WeekSelection {
  const requestedSeason = parseInteger(params.get('season'), 2000, 2100)
  const requestedWeek = parseInteger(params.get('week'), 1, 18)
  const needsDefault = requestedSeason === null || requestedWeek === null

  const currentWeek = useQuery({
    queryKey: ['current-week'],
    queryFn: () => apiFetch<CurrentWeek>('/api/weeks/current'),
    enabled: needsDefault,
  })

  const fallback: CurrentWeek | null = needsDefault && currentWeek.isSuccess ? (currentWeek.data ?? fallbackWeek()) : null
  return {
    season: requestedSeason ?? fallback?.season ?? null,
    week: requestedWeek ?? fallback?.week ?? null,
    resolving: needsDefault && currentWeek.isPending,
    error: currentWeek.isError ? currentWeek.error : null,
  }
}
