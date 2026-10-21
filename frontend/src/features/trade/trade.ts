import type { TradeAnalysis, TradePlayerValue } from '../../api/types.ts'
import { isInjuryOverride } from '../rankings/factors.ts'

export const MAX_PER_SIDE = 5

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const EVEN = 0.005

/** The comma-separated ids in the address: valid ones only, no repeats, no more than a side can hold. */
export function parseIds(value: string | null): string[] {
  if (!value) return []
  const ids = value.split(',').filter((id) => UUID.test(id))
  return [...new Set(ids)].slice(0, MAX_PER_SIDE)
}

export function sameSelection(a: string[], b: string[]): boolean {
  return a.length === b.length && [...a].sort().join() === [...b].sort().join()
}

export interface Outcome {
  /** Value of the side you get minus the side you give; positive means you receive more. */
  advantage: number
  headline: string
}

/** Side A is what you give and side B what you get, and the server's delta is A minus B. */
export function outcomeOf(analysis: TradeAnalysis): Outcome {
  const advantage = -analysis.valueDelta
  const amount = Math.abs(advantage).toFixed(2)
  if (Math.abs(advantage) < EVEN) return { advantage: 0, headline: 'The two sides are worth the same.' }
  return {
    advantage,
    headline: advantage > 0 ? `The side you get is worth ${amount} more points of value.` : `The side you give is worth ${amount} more points of value.`,
  }
}

export function formatValue(value: number): string {
  if (Math.abs(value) < EVEN) return '0.00'
  return `${value < 0 ? '−' : '+'}${Math.abs(value).toFixed(2)}`
}

export function formatPlain(value: number): string {
  return `${value < 0 ? '−' : ''}${Math.abs(value).toFixed(2)}`
}

export function ruledOutPlayers(analysis: TradeAnalysis): TradePlayerValue[] {
  return [...analysis.sideA.players, ...analysis.sideB.players].filter((player) => player.factors.some(isInjuryOverride))
}
