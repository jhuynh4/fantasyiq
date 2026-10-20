import { describe, expect, it } from 'vitest'
import type { Factor } from '../../api/types.ts'
import { ZERO_AT, arithmetic, layoutBars } from './evidence.ts'

function factor(factorType: string, contribution: number): Factor {
  return { factorType, contribution, narrative: '' }
}

describe('layoutBars', () => {
  it('draws equal points at equal lengths, positives to the right of zero and negatives to the left', () => {
    const [positive, negative, double] = layoutBars([factor('MATCHUP', 5), factor('WEATHER', -5), factor('USAGE', 10)])

    expect(positive.left).toBe(ZERO_AT)
    expect(negative.left + negative.width).toBeCloseTo(ZERO_AT)
    expect(negative.width).toBeCloseTo(positive.width)
    expect(double.width).toBeCloseTo(positive.width * 2)
  })

  it('keeps the longest bar on each side inside the bar', () => {
    const bars = layoutBars([factor('MATCHUP', 40), factor('WEATHER', -40)])

    for (const bar of bars) {
      expect(bar.left).toBeGreaterThanOrEqual(0)
      expect(bar.left + bar.width).toBeLessThanOrEqual(1)
    }
  })

  it('fills the left lane for a player who is ruled out rather than scaling -1000', () => {
    const [injury, matchup] = layoutBars([factor('INJURY', -1000), factor('MATCHUP', 10)])

    expect(injury.negative).toBe(true)
    expect(injury.left).toBeGreaterThanOrEqual(0)
    expect(injury.left + injury.width).toBeCloseTo(ZERO_AT)
    expect(matchup.width).toBeGreaterThan(0.5)
  })

  it('handles factors that all contribute nothing', () => {
    expect(layoutBars([factor('MATCHUP', 0)])).toEqual([{ left: ZERO_AT, width: 0, negative: false }])
  })
})

describe('arithmetic', () => {
  it('writes the factors out as a sum that ends in the score', () => {
    expect(arithmetic([18.55, 10, -7.5, 3, 9.54], 33.59)).toBe('18.55 + 10.00 − 7.50 + 3.00 + 9.54 = 33.59')
  })

  it('shows a negative first term and a negative total with their signs', () => {
    expect(arithmetic([-1000, 11.09], -988.91)).toBe('−1000.00 + 11.09 = −988.91')
  })
})
