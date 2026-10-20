import { CircleMinus, CirclePlus } from 'lucide-react'
import type { StartSitRecommendation } from '../../api/types.ts'
import { formatSigned, isInjuryOverride, labelFor, sortFactors } from '../rankings/factors.ts'
import { ZERO_AT, arithmetic, layoutBars } from './evidence.ts'

export function FactorEvidence({ recommendation }: { recommendation: StartSitRecommendation }) {
  const factors = sortFactors(recommendation.factors)
  const bars = layoutBars(factors)

  return (
    <section className="card evidence" aria-labelledby="evidence-heading">
      <div className="evidence-heading">
        <h2 id="evidence-heading">Why this score</h2>
        <p className="muted small">
          {factors.length === 1 ? 'One contribution' : `${factors.length} contributions, added together`}. + adds
          points; − reduces points. The vertical tick marks zero.
        </p>
      </div>

      <ol className="factor-list">
        {factors.map((factor, index) => {
          const bar = bars[index]
          const negative = factor.contribution < 0
          const Icon = negative ? CircleMinus : CirclePlus
          return (
            <li key={factor.factorType} className="factor-row">
              <div className="factor-title">
                <h3>{labelFor(factor.factorType)}</h3>
                <span className={`factor-points mono ${negative ? 'negative' : 'positive'}`}>
                  <Icon size={14} aria-hidden="true" />
                  {isInjuryOverride(factor) ? 'Out' : formatSigned(factor.contribution)}
                </span>
              </div>
              <div className="signed-bar" aria-hidden="true">
                <i className="signed-track" />
                <i
                  className={`signed-fill ${bar.negative ? 'fill-negative' : 'fill-positive'}`}
                  style={{ left: `${bar.left * 100}%`, width: `${bar.width * 100}%` }}
                />
                <i className="signed-zero" style={{ left: `${ZERO_AT * 100}%` }} />
              </div>
              <p className="muted small">{factor.narrative}</p>
            </li>
          )
        })}
      </ol>

      <div className="math-box">
        <p className="eyebrow-small accent">The math, in full</p>
        <p className="mono math-line">
          {arithmetic(
            factors.map((factor) => factor.contribution),
            recommendation.score,
          )}
        </p>
      </div>
    </section>
  )
}
