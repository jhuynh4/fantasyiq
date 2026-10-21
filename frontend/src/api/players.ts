import { apiFetch } from './client.ts'
import type { PlayerDetail, PlayerSummary } from './types.ts'

export async function fetchPlayer(id: string): Promise<PlayerDetail> {
  const player = await apiFetch<PlayerDetail>(`/api/players/${id}`)
  if (player === null) throw new Error('The server returned no player.')
  return player
}

export async function searchPlayers(query: string): Promise<PlayerSummary[]> {
  return (await apiFetch<PlayerSummary[]>(`/api/players/search?${new URLSearchParams({ q: query })}`)) ?? []
}
