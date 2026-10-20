import type { Factor } from '../../api/types.ts'

export interface FactorMeta {
  label: string
  /** CSS variable holding the segment color. */
  color: string
}

// Listed in the order segments are drawn, so a factor sits in the same place on every row.
export const FACTOR_META: Record<string, FactorMeta> = {
  RECENT_PERFORMANCE: { label: 'Recent form', color: 'var(--factor-recent)' },
  USAGE: { label: 'Usage trend', color: 'var(--factor-usage)' },
  MATCHUP: { label: 'Matchup', color: 'var(--factor-matchup)' },
  VEGAS: { label: 'Vegas implied total', color: 'var(--factor-vegas)' },
  WEATHER: { label: 'Weather', color: 'var(--factor-weather)' },
  INJURY: { label: 'Injury status', color: 'var(--factor-injury)' },
}

const FACTOR_ORDER = Object.keys(FACTOR_META)

/** An OUT/IR player's injury penalty is -1000 so nothing can outweigh it; it is not a point value to draw to scale. */
const INJURY_OVERRIDE_THRESHOLD = -100

export function isInjuryOverride(factor: Factor): boolean {
  return factor.factorType === 'INJURY' && factor.contribution < INJURY_OVERRIDE_THRESHOLD
}

export function labelFor(factorType: string): string {
  return FACTOR_META[factorType]?.label ?? factorType
}

export function sortFactors(factors: Factor[]): Factor[] {
  const rank = (type: string) => {
    const index = FACTOR_ORDER.indexOf(type)
    return index === -1 ? FACTOR_ORDER.length : index
  }
  return [...factors].sort((a, b) => rank(a.factorType) - rank(b.factorType))
}

export function formatSigned(value: number): string {
  return `${value < 0 ? '−' : '+'}${Math.abs(value).toFixed(2)}`
}
