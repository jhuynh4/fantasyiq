import { isInjuryOverride } from '../rankings/factors.ts'
import { ConfidenceBadge } from '../rankings/RankingsTable.tsx'
import type { StartSitRecommendation } from '../../api/types.ts'

const CONFIDENCE_TEXT = {
  HIGH: { title: 'Well supported', body: 'The average rests on six or more games of evidence, counting last season for up to four.' },
  MEDIUM: { title: 'Partly supported', body: 'The average rests on three to five games of evidence, counting last season for up to four.' },
  LOW: { title: 'Thinly supported', body: 'The average rests on only one or two games of evidence.' },
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

  return (
    <aside className="card score-context">
      <h2>Read the score, not a verdict.</h2>
      <p className="muted">
        {recommendation.score.toFixed(2)} is the model&rsquo;s expected PPR points for this week: the player&rsquo;s season
        average, adjusted for the opponent, the betting line, the weather and injury status. It is a forecast, not a
        guarantee.
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
