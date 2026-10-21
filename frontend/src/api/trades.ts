import { apiFetch } from './client.ts'
import type { TradeAnalysis } from './types.ts'

export async function analyzeTrade(sideAPlayerIds: string[], sideBPlayerIds: string[]): Promise<TradeAnalysis> {
  const analysis = await apiFetch<TradeAnalysis>('/api/trades/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sideAPlayerIds, sideBPlayerIds }),
  })
  if (analysis === null) throw new Error('The server returned no analysis.')
  return analysis
}
