import { Info } from 'lucide-react'
import { FACTOR_META, START_SIT_FACTORS } from './factors.ts'
import { ConfidenceBadge } from './RankingsTable.tsx'

export function FactorLegend() {
  return (
    <div className="table-key">
      <ul className="legend">
        {START_SIT_FACTORS.map((type) => (
          <li key={type}>
            <i className="swatch" style={{ background: FACTOR_META[type].color }} />
            {FACTOR_META[type].label}
          </li>
        ))}
      </ul>
      <p className="muted small">
        Read the shape: − left of zero reduces the score; + right adds to it. Segment length shows points, not
        confidence. An injured player ruled out for the week shows a full-width bar to the left.
      </p>
      <p className="select-hint">Select a player to see the full score breakdown →</p>
    </div>
  )
}

export function ConfidenceGuide() {
  return (
    <section className="card confidence-guide" aria-labelledby="confidence-heading">
      <h2 id="confidence-heading">
        <Info size={15} aria-hidden="true" />
        What does confidence mean?
      </h2>
      <p className="muted">
        Confidence describes how many games of evidence the player&rsquo;s average rests on (this season&rsquo;s, with last season counting for up to four), not the chance of a
        particular performance.
      </p>
      <dl>
        <div>
          <dt>
            <ConfidenceBadge level="HIGH" />
          </dt>
          <dd className="muted small">Six or more games of evidence.</dd>
        </div>
        <div>
          <dt>
            <ConfidenceBadge level="MEDIUM" />
          </dt>
          <dd className="muted small">Three to five games of evidence.</dd>
        </div>
        <div>
          <dt>
            <ConfidenceBadge level="LOW" />
          </dt>
          <dd className="muted small">One or two games of evidence, or the player is listed out or on injured reserve.</dd>
        </div>
      </dl>
    </section>
  )
}
