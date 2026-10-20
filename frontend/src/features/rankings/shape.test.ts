import { describe, expect, it } from 'vitest'
import type { Factor } from '../../api/types.ts'
import { BASELINE, layoutSegments, positiveTotal } from './shape.ts'

function factor(factorType: string, contribution: number): Factor {
  return { factorType, contribution, narrative: '' }
}

describe('layoutSegments', () => {
  it('puts positive factors to the right of the zero line, in a fixed order, and negative ones to the left', () => {
    const factors = [factor('WEATHER', -2), factor('MATCHUP', 6), factor('RECENT_PERFORMANCE', 10)]

    const segments = layoutSegments(factors, positiveTotal(factors))

    const byType = Object.fromEntries(segments.map((segment) => [segment.factorType, segment]))
    expect(byType.RECENT_PERFORMANCE.left).toBeLessThan(byType.MATCHUP.left)
    expect(byType.MATCHUP.left).toBeGreaterThan(BASELINE)
    expect(byType.WEATHER.left + byType.WEATHER.width).toBeCloseTo(BASELINE)
  })

  it('draws one point at the same length on every row, scaled to the largest row', () => {
    const small = [factor('MATCHUP', 5)]
    const large = [factor('MATCHUP', 20)]

    const [smallSegment] = layoutSegments(small, 20)
    const [largeSegment] = layoutSegments(large, 20)

    expect(largeSegment.width).toBeCloseTo(smallSegment.width * 4)
    expect(largeSegment.left + largeSegment.width).toBeCloseTo(1)
  })

  it('stacks several negative factors leftward from the zero line without leaving the lane', () => {
    const factors = [factor('WEATHER', -30), factor('MATCHUP', -30), factor('RECENT_PERFORMANCE', 10)]

    const negatives = layoutSegments(factors, 10).filter((segment) => segment.left < BASELINE)

    expect(negatives).toHaveLength(2)
    for (const segment of negatives) expect(segment.left).toBeGreaterThanOrEqual(0)
  })

  it('shows a ruled-out player as one full-width bar instead of drawing -1000 points to scale', () => {
    const factors = [factor('INJURY', -1000), factor('RECENT_PERFORMANCE', 10)]

    const segments = layoutSegments(factors, 10)
    const injury = segments.find((segment) => segment.factorType === 'INJURY')

    expect(injury).toEqual({ factorType: 'INJURY', left: 0, width: BASELINE })
  })

  it('skips factors that contributed nothing and copes with a list of all zeroes', () => {
    expect(layoutSegments([factor('MATCHUP', 0)], 0)).toEqual([])
  })
})
