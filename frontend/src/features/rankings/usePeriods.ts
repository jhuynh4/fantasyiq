import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../api/client.ts'
import type { RecommendationPeriod } from '../../api/types.ts'

/**
 * The seasons and weeks that have scores. `null` until the answer arrives, and if the request
 * fails, in which case the pickers fall back to offering every week rather than breaking.
 */
export function useAvailablePeriods(): { periods: RecommendationPeriod[] | null } {
  const query = useQuery({
    queryKey: ['recommendation-periods'],
    queryFn: async () => (await apiFetch<RecommendationPeriod[]>('/api/recommendations/periods')) ?? [],
    staleTime: 60_000,
  })
  return { periods: query.data ?? null }
}
