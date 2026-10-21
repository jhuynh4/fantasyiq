import { CircleMinus, CirclePlus } from 'lucide-react'
import type { Factor } from '../../api/types.ts'
import { arithmetic } from '../player/evidence.ts'
import { formatSigned, isInjuryOverride, labelFor, sortFactors } from '../rankings/factors.ts'
import { type FactorComparison, type Side, describeRow, formatDifference, moreFor } from './compare.ts'

function Cell({ factor, scale, name }: { factor: Factor | null; scale: number; name: string }) {
  if (factor === null) {
    return (
      <div className="cmp-cell">
        <span className="cmp-who muted small">{name}</span>
        <span className="muted">No data</span>
      </div>
    )
  }
  const negative = factor.contribution < 0
  const Icon = negative ? CircleMinus : CirclePlus
  const width = isInjuryOverride(factor) ? 100 : Math.min(100, Math.abs(factor.contribution) * scale * 100)
  return (
    <div className="cmp-cell">
      <span className="cmp-who muted small">{name}</span>
      <span className={`cmp-value mono ${negative ? 'negative' : 'positive'}`}>
        <Icon size={13} aria-hidden="true" />
        {isInjuryOverride(factor) ? 'Out' : formatSigned(factor.contribution)}
      </span>
      <i className={`cmp-bar ${negative ? 'fill-negative' : 'fill-positive'}`} style={{ width: `${width}%` }} />
    </div>
  )
}

export function FactorComparisonTable({ a, b, rows }: { a: Side; b: Side; rows: FactorComparison[] }) {
  // One scale for the whole table, so equal points are equal lengths everywhere in it.
  const largest = Math.max(
    0,
    ...rows.flatMap((row) => [row.a, row.b]).flatMap((factor) => (factor && !isInjuryOverride(factor) ? [Math.abs(factor.contribution)] : [])),
  )
  const scale = largest > 0 ? 1 / largest : 0

  return (
    <section className="card factor-comparison" aria-labelledby="gap-heading">
      <div className="comparison-intro">
        <h2 id="gap-heading">Where the gap comes from</h2>
        <p className="muted small">
          All values are signed score contributions. Δ = {a.name} minus {b.name}.
        </p>
      </div>

      <div className="cmp-head" aria-hidden="true">
        <span>Factor</span>
        <span className="cmp-head-a">{a.name}</span>
        <span>{b.name}</span>
        <span>Difference (Δ)</span>
      </div>

      <ol className="cmp-rows">
        {rows.map((row) => (
          <li key={row.factorType} className="cmp-row">
            <h3 className="cmp-factor">{labelFor(row.factorType)}</h3>
            <Cell factor={row.a} scale={scale} name={a.name} />
            <Cell factor={row.b} scale={scale} name={b.name} />
            <div className="cmp-diff">
              <span className="mono cmp-diff-value">{formatDifference(row.difference)}</span>
              <span className="muted small">{moreFor(row.difference, a.name, b.name)}</span>
            </div>
            <p className="cmp-sentence muted small">{describeRow(row, a.name, b.name)}</p>
          </li>
        ))}
      </ol>

      <div className="reconciled">
        <p>
          <strong>Every factor is included in the total.</strong>
        </p>
        {[a, b].map((side) => (
          <p key={side.name} className="mono small">
            {side.name}:{' '}
            {arithmetic(
              sortFactors(side.factors).map((factor) => factor.contribution),
              side.score,
            )}
          </p>
        ))}
      </div>
    </section>
  )
}
