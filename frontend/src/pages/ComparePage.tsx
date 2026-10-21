import { useQuery } from '@tanstack/react-query'
import { Info } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { fetchPlayer } from '../api/players.ts'
import { fetchStartSit } from '../api/recommendations.ts'
import type { Position } from '../api/types.ts'
import { PeriodPickers } from '../components/PeriodPickers.tsx'
import { FactorComparisonTable } from '../features/compare/FactorComparisonTable.tsx'
import { type CardData, PlayerCard } from '../features/compare/PlayerCard.tsx'
import { type Side, compareFactors, describeTradeOff, isEven } from '../features/compare/compare.ts'
import { isPosition } from '../features/rankings/period.ts'
import { useWeekSelection } from '../features/rankings/useWeekSelection.ts'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function idParam(params: URLSearchParams, key: string): string | null {
  const value = params.get(key)
  return value !== null && UUID.test(value) ? value : null
}

/** One side of the comparison: the player's profile, and their score for the week. */
function useSide(id: string | null, season: number | null, week: number | null): CardData {
  const player = useQuery({
    queryKey: ['player', id],
    queryFn: () => fetchPlayer(id!),
    enabled: id !== null,
  })
  const position: Position | null = player.data && isPosition(player.data.position) ? player.data.position : null
  const rankings = useQuery({
    queryKey: ['start-sit', season, week, position],
    queryFn: () => fetchStartSit(season!, week!, position!),
    enabled: season !== null && week !== null && position !== null,
  })
  const index = rankings.data?.findIndex((row) => row.playerId === id) ?? -1
  return {
    player: player.data ?? null,
    loading: id !== null && player.isPending,
    recommendation: index >= 0 ? rankings.data![index] : null,
    rank: index >= 0 ? index + 1 : null,
    scoreLoaded: rankings.isSuccess,
  }
}

export function ComparePage() {
  const [params, setParams] = useSearchParams()
  const { season, week, resolving, error: weekError } = useWeekSelection(params)
  const aId = idParam(params, 'a')
  const bId = idParam(params, 'b')
  const a = useSide(aId, season, week)
  const b = useSide(bId, season, week)

  const ready = season !== null && week !== null

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    if (season !== null) next.set('season', String(season))
    if (week !== null) next.set('week', String(week))
    for (const [key, value] of Object.entries(changes)) next.set(key, value)
    setParams(next)
  }

  const aPosition = a.player && isPosition(a.player.position) ? a.player.position : null
  const bPosition = b.player && isPosition(b.player.position) ? b.player.position : null
  const mixed = aPosition !== null && bPosition !== null && aPosition !== bPosition

  const both = a.recommendation && b.recommendation && a.player && b.player ? { a: a.recommendation, b: b.recommendation } : null
  const sideA: Side | null = both && a.player ? { name: a.player.fullName, score: both.a.score, confidence: both.a.confidence, factors: both.a.factors } : null
  const sideB: Side | null = both && b.player ? { name: b.player.fullName, score: both.b.score, confidence: both.b.confidence, factors: both.b.factors } : null
  const rows = sideA && sideB ? compareFactors(sideA.factors, sideB.factors) : null
  const gap = sideA && sideB ? sideA.score - sideB.score : 0
  const leader = sideA && sideB && !isEven(gap) ? (gap > 0 ? sideA : sideB) : null
  const trailing = leader && sideA && sideB ? (leader === sideA ? sideB : sideA) : null

  return (
    <div className="compare-page">
      <div className="page-heading">
        <div className="title-row">
          <h1 className="page-title">Compare players</h1>
          {ready && (
            <PeriodPickers
              season={season}
              week={week}
              onSeasonChange={(value) => update({ season: String(value) })}
              onWeekChange={(value) => update({ week: String(value) })}
            />
          )}
        </div>
        <p className="muted">Two players. The same model. See exactly where their scores differ.</p>
      </div>

      {weekError && (
        <p className="form-error" role="alert">
          {weekError.message}
        </p>
      )}
      {resolving && <p className="page-status">Loading…</p>}

      <div className="compare-cards">
        <PlayerCard
          slot="First player"
          heading={leader === sideA && sideA ? 'Higher score' : 'First player'}
          highlighted={leader !== null && leader === sideA}
          data={a}
          onlyPosition={bPosition}
          excludeId={bId}
          onPick={(picked) => update({ a: picked.id })}
        />
        <PlayerCard
          slot="Second player"
          heading={leader === sideB && sideB ? 'Higher score' : 'Comparison'}
          highlighted={leader !== null && leader === sideB}
          data={b}
          onlyPosition={aPosition}
          excludeId={aId}
          onPick={(picked) => update({ b: picked.id })}
        />
      </div>

      {mixed && (
        <p className="notice" role="note">
          <Info size={16} aria-hidden="true" />
          These players play different positions. Scores are built the same way for everyone, but they are meant to be
          compared within a position, so treat this gap with care.
        </p>
      )}

      {sideA && sideB && rows && (
        <>
          <div className="gap-box">
            <p className="gap-value mono">
              {isEven(gap) ? '0.00' : `${gap > 0 ? '+' : '−'}${Math.abs(gap).toFixed(2)}`}
            </p>
            <div>
              <p className="gap-title">
                {leader ? `${leader.name} has the higher model score.` : 'The two players have the same model score.'}
              </p>
              <p className="muted small">
                {leader && trailing
                  ? `${leader.score.toFixed(2)} − ${trailing.score.toFixed(2)} = ${Math.abs(gap).toFixed(2)} points. `
                  : ''}
                A score difference, not a lineup recommendation.
              </p>
            </div>
          </div>

          <FactorComparisonTable a={sideA} b={sideB} rows={rows} />

          <aside className="notice" aria-labelledby="tradeoff-heading">
            <Info size={16} aria-hidden="true" />
            <div>
              <h2 id="tradeoff-heading">The main trade-off</h2>
              <p className="muted small">{describeTradeOff(sideA, sideB, rows)}</p>
            </div>
          </aside>
        </>
      )}

      {!both && (a.player || b.player) && ready && (
        <p className="muted panel-status">
          {a.player && b.player
            ? 'Both players need a score for this week to be compared. Try another week.'
            : 'Choose a second player to compare.'}
        </p>
      )}
      {!a.player && !b.player && !a.loading && !b.loading && (
        <p className="muted panel-status">Search for two players at the same position to compare them.</p>
      )}

      <p className="muted small data-note">Scores describe the model&apos;s view of the week, not guaranteed outcomes.</p>
    </div>
  )
}
