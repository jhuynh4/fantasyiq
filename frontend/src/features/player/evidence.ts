import type { Factor } from '../../api/types.ts'
import { isInjuryOverride } from '../rankings/factors.ts'

/** Where the zero tick sits along the bar, as a fraction of its width. */
export const ZERO_AT = 0.28
const POSITIVE_LANE = 0.7
const NEGATIVE_LANE = 0.26

export interface Bar {
  left: number
  width: number
  negative: boolean
}

/**
 * One bar per factor, all on the same scale (points per unit of width), so a 10-point factor is
 * twice as long as a 5-point one. The scale is chosen so the longest bar on each side still fits;
 * a ruled-out player's -1000 injury penalty fills the left lane instead of being drawn to scale.
 */
export function layoutBars(factors: Factor[]): Bar[] {
  const scalable = factors.filter((factor) => !isInjuryOverride(factor))
  const largestPositive = Math.max(0, ...scalable.map((factor) => factor.contribution))
  const largestNegative = Math.max(0, ...scalable.map((factor) => -factor.contribution))
  const perPoint = Math.min(
    largestPositive > 0 ? POSITIVE_LANE / largestPositive : Infinity,
    largestNegative > 0 ? NEGATIVE_LANE / largestNegative : Infinity,
  )
  const scale = Number.isFinite(perPoint) ? perPoint : 0

  return factors.map((factor) => {
    if (isInjuryOverride(factor)) return { left: ZERO_AT - NEGATIVE_LANE, width: NEGATIVE_LANE, negative: true }
    const width = Math.abs(factor.contribution) * scale
    return factor.contribution < 0
      ? { left: ZERO_AT - width, width, negative: true }
      : { left: ZERO_AT, width, negative: false }
  })
}

function magnitude(value: number): string {
  return Math.abs(value).toFixed(2)
}

/** "18.55 + 10.00 − 7.50 = 21.05": the factors, added together, in the order shown. */
export function arithmetic(contributions: number[], score: number): string {
  const terms = contributions.map((value, index) => {
    if (index === 0) return `${value < 0 ? '−' : ''}${magnitude(value)}`
    return `${value < 0 ? '−' : '+'} ${magnitude(value)}`
  })
  return `${terms.join(' ')} = ${score < 0 ? '−' : ''}${magnitude(score)}`
}
