import type { Factor } from '../../api/types.ts'
import { isInjuryOverride, sortFactors } from './factors.ts'

/** Where the zero line sits, as a fraction of the shape's width; negatives grow left of it, positives right. */
export const BASELINE = 0.186
const POSITIVE_START = 0.2
const POSITIVE_LANE = 1 - POSITIVE_START
const MIN_SEGMENT = 0.012

export interface Segment {
  factorType: string
  /** Fractions of the shape's width. */
  left: number
  width: number
}

/** The positive points in a row; the largest of these over a list sets the scale for the whole list. */
export function positiveTotal(factors: Factor[]): number {
  return factors.reduce((sum, factor) => (factor.contribution > 0 ? sum + factor.contribution : sum), 0)
}

/**
 * Lays a row's factors out around the zero line. `maxPositive` is the largest positive total in the
 * list being shown, so one point is the same length on every row and rows can be compared by eye.
 */
export function layoutSegments(factors: Factor[], maxPositive: number): Segment[] {
  const perPoint = maxPositive > 0 ? POSITIVE_LANE / maxPositive : 0
  const ordered = sortFactors(factors).filter((factor) => factor.contribution !== 0)
  const segments: Segment[] = []

  let cursor = POSITIVE_START
  for (const factor of ordered) {
    if (factor.contribution <= 0) continue
    const width = Math.max(factor.contribution * perPoint, MIN_SEGMENT)
    segments.push({ factorType: factor.factorType, left: cursor, width })
    cursor += width
  }

  const negatives = ordered.filter((factor) => factor.contribution < 0)
  const override = negatives.find(isInjuryOverride)
  if (override) {
    segments.push({ factorType: override.factorType, left: 0, width: BASELINE })
    return segments
  }

  const negativeTotal = negatives.reduce((sum, factor) => sum - factor.contribution, 0) * perPoint
  const squeeze = negativeTotal > BASELINE ? BASELINE / negativeTotal : 1
  let edge = BASELINE
  for (const factor of negatives) {
    const width = Math.max(-factor.contribution * perPoint * squeeze, MIN_SEGMENT)
    edge -= width
    segments.push({ factorType: factor.factorType, left: edge, width })
  }
  return segments
}
