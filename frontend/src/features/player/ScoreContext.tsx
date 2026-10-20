import { isInjuryOverride } from '../rankings/factors.ts'
import { ConfidenceBadge } from '../rankings/RankingsTable.tsx'
import type { StartSitRecommendation } from '../../api/types.ts'

const TOTAL_FACTORS = 6

const CONFIDENCE_TEXT = {
  HIGH: { title: 'Well supported', body: 'Four or more of the six factors had data for this player.' },
  MEDIUM: { title: 'Partly supported', body: 'Two or three of the six factors had data for this player.' },
  LOW: { title: 'Thinly supported', body: 'Only one factor had data for this player.' },
} as const

export function ScoreContext({
  recommendation,
  season,
  week,
}: {
  recommendation: StartSitRecommendation
  season: number
  week: number
}) {
  const ruledOut = recommendation.factors.some(isInjuryOverride)
  const text = CONFIDENCE_TEXT[recommendation.confidence]
  const factorCount = recommendation.factors.length

  return (
    <aside className="card score-context">
      <h2>Read the score, not a verdict.</h2>
      <p className="muted">
        {recommendation.score.toFixed(2)} is a model score built from {factorCount} of {TOTAL_FACTORS} possible factors.
        It helps compare players within this week and position; it is not a fantasy-point total.
      </p>
      <div className="context-section">
        <ConfidenceBadge level={recommendation.confidence} />
        <h3>{ruledOut ? 'Ruled out' : text.title}</h3>
        <p className="muted small">
          {ruledOut
            ? 'The player is listed out or on injured reserve, so confidence is always low and the injury penalty outweighs every other factor.'
            : text.body}{' '}
          Confidence reflects how much data went in, not the chance of a particular performance.
        </p>
      </div>
      <div className="context-section">
        <p className="eyebrow-small">Model snapshot</p>
        <p className="small">
          Season {season} · Week {week}
        </p>
      </div>
    </aside>
  )
}
