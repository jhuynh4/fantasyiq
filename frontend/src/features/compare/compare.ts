import type { Confidence, Factor } from '../../api/types.ts'
import { FACTOR_META, isInjuryOverride, labelFor } from '../rankings/factors.ts'

/** What each side contributed for one factor; null means the model had no data for that player. */
export interface FactorComparison {
  factorType: string
  a: Factor | null
  b: Factor | null
  /** a - b, counting a missing factor as 0. */
  difference: number
}

export interface Side {
  name: string
  score: number
  confidence: Confidence
  factors: Factor[]
}

const EVEN = 0.005

export function isEven(value: number): boolean {
  return Math.abs(value) < EVEN
}

export function compareFactors(a: Factor[], b: Factor[]): FactorComparison[] {
  const types = [...new Set([...a, ...b].map((factor) => factor.factorType))]
  const order = Object.keys(FACTOR_META)
  const rank = (type: string) => {
    const index = order.indexOf(type)
    return index === -1 ? order.length : index
  }
  return types
    .sort((x, y) => rank(x) - rank(y))
    .map((type) => {
      const left = a.find((factor) => factor.factorType === type) ?? null
      const right = b.find((factor) => factor.factorType === type) ?? null
      return { factorType: type, a: left, b: right, difference: (left?.contribution ?? 0) - (right?.contribution ?? 0) }
    })
}

function points(value: number): string {
  return Math.abs(value).toFixed(2)
}

export function formatDifference(value: number): string {
  if (isEven(value)) return '0.00'
  return `${value < 0 ? '−' : '+'}${points(value)}`
}

/** "More for Josh Allen", "More for Lamar Jackson" or "Even". */
export function moreFor(difference: number, aName: string, bName: string): string {
  if (isEven(difference)) return 'Even'
  return `More for ${difference > 0 ? aName : bName}`
}

/** One plain sentence on what separates the two players for a factor. */
export function describeRow(row: FactorComparison, aName: string, bName: string): string {
  const label = labelFor(row.factorType).toLowerCase()
  const missing = row.a === null ? aName : row.b === null ? bName : null
  if (missing) return `${missing} has no ${label} data, so it counts as zero.`
  if (isEven(row.difference)) return `Both players get the same from ${label}.`
  const winner = row.difference > 0 ? aName : bName
  return `${winner} gets ${points(row.difference)} more points from ${label}.`
}

function listOf(labels: string[]): string {
  if (labels.length <= 1) return labels.join('')
  return `${labels.slice(0, -1).join(', ')} and ${labels[labels.length - 1]}`
}

/** The "main trade-off" paragraph: who gains how much, and what offsets it. */
export function describeTradeOff(a: Side, b: Side, rows: FactorComparison[]): string {
  const gap = a.score - b.score
  const confidence =
    a.confidence === b.confidence
      ? `Both players have ${a.confidence} confidence.`
      : `${a.name} has ${a.confidence} confidence and ${b.name} has ${b.confidence}.`
  const caveat = 'Confidence reflects how much data went in, not certainty of performance.'

  const ruledOut = [a, b].filter((side) => side.factors.some(isInjuryOverride))
  if (ruledOut.length > 0) {
    const names = listOf(ruledOut.map((side) => side.name))
    return `${names} ${ruledOut.length > 1 ? 'are' : 'is'} listed out or on injured reserve, and that penalty outweighs every other factor. ${confidence} ${caveat}`
  }
  if (isEven(gap)) {
    return `The two scores are level. Factor by factor they differ, but the differences cancel out. ${confidence} ${caveat}`
  }

  const leaderIsA = gap > 0
  const leader = leaderIsA ? a : b
  const trailing = leaderIsA ? b : a
  const toLeader = (row: FactorComparison) => (leaderIsA ? row.difference : -row.difference)
  const leaderRows = rows.filter((row) => toLeader(row) > EVEN)
  const trailingRows = rows.filter((row) => toLeader(row) < -EVEN)
  const leaderGain = leaderRows.reduce((sum, row) => sum + toLeader(row), 0)
  const trailingGain = trailingRows.reduce((sum, row) => sum - toLeader(row), 0)
  const names = (list: FactorComparison[]) => listOf(list.map((row) => labelFor(row.factorType).toLowerCase()))

  const first = `${leader.name} gains ${points(leaderGain)} points from ${names(leaderRows)}.`
  const second =
    trailingRows.length > 0
      ? `${trailing.name}'s edge in ${names(trailingRows)} offsets ${points(trailingGain)} of those points, leaving a ${points(gap)}-point gap.`
      : `No factor favors ${trailing.name}, so the whole ${points(gap)}-point gap comes from those factors.`
  return `${first} ${second} ${confidence} ${caveat}`
}
