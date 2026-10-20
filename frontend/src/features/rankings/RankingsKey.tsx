import { Info } from 'lucide-react'
import { FACTOR_META } from './factors.ts'
import { ConfidenceBadge } from './RankingsTable.tsx'

export function FactorLegend() {
  return (
    <div className="table-key">
      <ul className="legend">
        {Object.values(FACTOR_META).map((meta) => (
          <li key={meta.label}>
            <i className="swatch" style={{ background: meta.color }} />
            {meta.label}
          </li>
        ))}
      </ul>
      <p className="muted small">
        Read the shape: − left of zero reduces the score; + right adds to it. Segment length shows points, not
        confidence. An injured player ruled out for the week shows a full-width bar to the left.
      </p>
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
        Confidence describes how much data went into a score, not the chance of a particular performance.
      </p>
      <dl>
        <div>
          <dt>
            <ConfidenceBadge level="HIGH" />
          </dt>
          <dd className="muted small">Four or more of the six factors had data.</dd>
        </div>
        <div>
          <dt>
            <ConfidenceBadge level="MEDIUM" />
          </dt>
          <dd className="muted small">Two or three factors had data.</dd>
        </div>
        <div>
          <dt>
            <ConfidenceBadge level="LOW" />
          </dt>
          <dd className="muted small">One factor, or the player is listed out or on injured reserve.</dd>
        </div>
      </dl>
    </section>
  )
}
