import type { Factor } from '../../api/types.ts'
import { FACTOR_META, formatSigned, isInjuryOverride, labelFor, sortFactors } from './factors.ts'
import { BASELINE, layoutSegments } from './shape.ts'

function describe(factors: Factor[]): string {
  return sortFactors(factors)
    .map((factor) => (isInjuryOverride(factor) ? `${labelFor(factor.factorType)}: out` : `${labelFor(factor.factorType)} ${formatSigned(factor.contribution)}`))
    .join(', ')
}

export function FactorShape({ factors, maxPositive }: { factors: Factor[]; maxPositive: number }) {
  const segments = layoutSegments(factors, maxPositive)
  return (
    <span className="factor-shape" role="img" aria-label={`Factor contributions: ${describe(factors)}`}>
      {segments.map((segment) => (
        <i
          key={segment.factorType}
          className="factor-segment"
          style={{
            left: `${segment.left * 100}%`,
            width: `${segment.width * 100}%`,
            background: FACTOR_META[segment.factorType]?.color ?? 'var(--muted)',
          }}
        />
      ))}
      <i className="factor-baseline" style={{ left: `${BASELINE * 100}%` }} />
    </span>
  )
}
