import { describe, expect, it } from 'vitest'
import { FACTOR_META, labelFor, sortFactors, START_SIT_FACTORS } from './factors.ts'
import type { Factor } from '../../api/types.ts'

function factor(factorType: string, contribution: number): Factor {
  return { factorType, contribution, narrative: '' } as Factor
}

describe('factor labels', () => {
  it('names the v2 baseline as the season average', () => {
    expect(labelFor('BASELINE')).toBe('Season average')
  })

  it('still labels the factors that only trade analysis uses', () => {
    expect(labelFor('RECENT_PERFORMANCE')).toBe('Recent form')
    expect(labelFor('USAGE')).toBe('Usage trend')
  })

  it('draws the baseline first so every row starts from the same place', () => {
    const sorted = sortFactors([factor('WEATHER', -1), factor('MATCHUP', 0.5), factor('BASELINE', 12)])

    expect(sorted.map((f) => f.factorType)).toEqual(['BASELINE', 'MATCHUP', 'WEATHER'])
  })

  it('lists only the factors a start/sit score is built from in the legend', () => {
    expect(START_SIT_FACTORS).toEqual(['BASELINE', 'MATCHUP', 'VEGAS', 'WEATHER', 'INJURY'])
    START_SIT_FACTORS.forEach((type) => expect(FACTOR_META[type]).toBeDefined())
  })
})
