import { apiFetch } from './client.ts'
import type { PlayerDetail } from './types.ts'

export async function fetchPlayer(id: string): Promise<PlayerDetail> {
  const player = await apiFetch<PlayerDetail>(`/api/players/${id}`)
  if (player === null) throw new Error('The server returned no player.')
  return player
}
