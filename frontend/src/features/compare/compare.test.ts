import { describe, expect, it } from 'vitest'
import type { Factor } from '../../api/types.ts'
import { type Side, compareFactors, describeRow, describeTradeOff, formatDifference, moreFor } from './compare.ts'

function factor(factorType: string, contribution: number): Factor {
  return { factorType, contribution, narrative: '' }
}

function side(name: string, factors: Factor[], confidence: Side['confidence'] = 'HIGH'): Side {
  return { name, confidence, factors, score: factors.reduce((sum, f) => sum + f.contribution, 0) }
}

describe('compareFactors', () => {
  it('lines the two players up factor by factor, in the model order, counting a missing factor as zero', () => {
    const rows = compareFactors(
      [factor('MATCHUP', 14), factor('RECENT_PERFORMANCE', 15)],
      [factor('USAGE', 10), factor('RECENT_PERFORMANCE', 12), factor('MATCHUP', 11)],
    )

    expect(rows.map((row) => row.factorType)).toEqual(['RECENT_PERFORMANCE', 'USAGE', 'MATCHUP'])
    expect(rows.map((row) => row.difference)).toEqual([3, -10, 3])
    expect(rows[1].a).toBeNull()
  })

  it('differences add up to the gap between the two scores', () => {
    const a = [factor('MATCHUP', 14.11), factor('USAGE', 12.75), factor('WEATHER', -2)]
    const b = [factor('MATCHUP', 11.29), factor('RECENT_PERFORMANCE', 13.17)]

    const total = compareFactors(a, b).reduce((sum, row) => sum + row.difference, 0)

    expect(total).toBeCloseTo(a.reduce((s, f) => s + f.contribution, 0) - b.reduce((s, f) => s + f.contribution, 0))
  })
})

describe('wording', () => {
  it('signs the difference and names who it favors', () => {
    expect(formatDifference(1.15)).toBe('+1.15')
    expect(formatDifference(-3.25)).toBe('−3.25')
    expect(formatDifference(0.001)).toBe('0.00')
    expect(moreFor(1.15, 'Josh Allen', 'Lamar Jackson')).toBe('More for Josh Allen')
    expect(moreFor(-3.25, 'Josh Allen', 'Lamar Jackson')).toBe('More for Lamar Jackson')
    expect(moreFor(0, 'Josh Allen', 'Lamar Jackson')).toBe('Even')
  })

  it('explains a row in a sentence, including when one player has no data', () => {
    const [matchup] = compareFactors([factor('MATCHUP', -7.5)], [factor('MATCHUP', -4.25)])
    expect(describeRow(matchup, 'Josh Allen', 'Lamar Jackson')).toBe('Lamar Jackson gets 3.25 more points from matchup.')

    const [usage] = compareFactors([], [factor('USAGE', 4)])
    expect(describeRow(usage, 'Josh Allen', 'Lamar Jackson')).toBe('Josh Allen has no usage trend data, so it counts as zero.')

    const [same] = compareFactors([factor('USAGE', 4)], [factor('USAGE', 4)])
    expect(describeRow(same, 'Josh Allen', 'Lamar Jackson')).toBe('Both players get the same from usage trend.')
  })
})

describe('describeTradeOff', () => {
  const allen = side('Josh Allen', [factor('RECENT_PERFORMANCE', 18), factor('USAGE', 10), factor('MATCHUP', -7.5)])
  const jackson = side('Lamar Jackson', [factor('RECENT_PERFORMANCE', 15), factor('USAGE', 8.5), factor('MATCHUP', -4.25)])

  it('says what the leader gains, what offsets it, and the resulting gap', () => {
    const text = describeTradeOff(allen, jackson, compareFactors(allen.factors, jackson.factors))

    expect(text).toContain('Josh Allen gains 4.50 points from recent form and usage trend.')
    expect(text).toContain("Lamar Jackson's edge in matchup offsets 3.25 of those points, leaving a 1.25-point gap.")
    expect(text).toContain('Both players have HIGH confidence.')
  })

  it('works the same way round when the second player leads', () => {
    const text = describeTradeOff(jackson, allen, compareFactors(jackson.factors, allen.factors))

    expect(text).toContain('Josh Allen gains 4.50 points')
  })

  it('does not invent an offset when no factor favors the trailing player', () => {
    const lead = side('A Player', [factor('USAGE', 10)])
    const trail = side('B Player', [factor('USAGE', 4)])

    expect(describeTradeOff(lead, trail, compareFactors(lead.factors, trail.factors))).toContain(
      'No factor favors B Player, so the whole 6.00-point gap comes from those factors.',
    )
  })

  it('names differing confidence levels', () => {
    const low = { ...jackson, confidence: 'LOW' as const }

    expect(describeTradeOff(allen, low, compareFactors(allen.factors, low.factors))).toContain(
      'Josh Allen has HIGH confidence and Lamar Jackson has LOW.',
    )
  })

  it('explains a ruled-out player rather than a points breakdown', () => {
    const hurt = side('Hurt Player', [factor('INJURY', -1000), factor('USAGE', 5)])
    const text = describeTradeOff(allen, hurt, compareFactors(allen.factors, hurt.factors))

    expect(text).toContain('Hurt Player is listed out or on injured reserve')
  })

  it('reports level scores as level', () => {
    const one = side('A Player', [factor('USAGE', 5)])
    const two = side('B Player', [factor('USAGE', 5)])

    expect(describeTradeOff(one, two, compareFactors(one.factors, two.factors))).toContain('The two scores are level.')
  })
})
