import { apiFetch } from './client.ts'
import type { Position, StartSitRecommendation } from './types.ts'

/** Already sorted by score, highest first. */
export async function fetchStartSit(season: number, week: number, position: Position): Promise<StartSitRecommendation[]> {
  const query = new URLSearchParams({ season: String(season), week: String(week), position })
  return (await apiFetch<StartSitRecommendation[]>(`/api/recommendations/start-sit?${query}`)) ?? []
}
