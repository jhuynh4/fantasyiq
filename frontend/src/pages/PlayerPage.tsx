import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Columns2 } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client.ts'
import { fetchPlayer } from '../api/players.ts'
import { fetchStartSit } from '../api/recommendations.ts'
import type { PlayerDetail, StartSitRecommendation } from '../api/types.ts'
import { PeriodPickers } from '../components/PeriodPickers.tsx'
import { FactorEvidence } from '../features/player/FactorEvidence.tsx'
import { ScoreContext } from '../features/player/ScoreContext.tsx'
import { ConfidenceBadge } from '../features/rankings/RankingsTable.tsx'
import { POSITION_NAMES, isPosition } from '../features/rankings/period.ts'
import { useWeekSelection } from '../features/rankings/useWeekSelection.ts'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function statusLabel(status: string): string {
  const lower = status.toLowerCase()
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}

function Summary({
  player,
  recommendation,
  rank,
  season,
  week,
}: {
  player: PlayerDetail
  recommendation: StartSitRecommendation | null
  rank: number | null
  season: number | null
  week: number | null
}) {
  const meta = [player.position, player.team, player.jerseyNumber !== null ? `#${player.jerseyNumber}` : null]
    .filter(Boolean)
    .join(' · ')
  return (
    <section className="card player-summary" aria-label="Player summary">
      <div className="player-identity">
        <div className="roster-meta">
          <span>{meta}</span>
          {player.status && (
            <span className={`status-badge ${player.status === 'ACTIVE' ? 'status-active' : ''}`}>
              {statusLabel(player.status)}
            </span>
          )}
        </div>
        <h1>{player.fullName}</h1>
        <p className="muted">
          {season !== null && week !== null ? `Weekly score breakdown · Season ${season}, week ${week}` : 'Weekly score breakdown'}
        </p>
        <Link className="secondary-button" to={`/compare?${new URLSearchParams({ ...(season !== null && week !== null ? { season: String(season), week: String(week) } : {}), a: player.id })}`}>
          <Columns2 size={16} aria-hidden="true" />
          Compare player
        </Link>
      </div>
      {recommendation && (
        <div className="score-standing">
          <div>
            <p className="eyebrow-small">Model score</p>
            <p className="model-score mono">{recommendation.score.toFixed(2)}</p>
          </div>
          <div className="standing">
            <p className="rank-line">
              #{rank} {recommendation.position}
            </p>
            <ConfidenceBadge level={recommendation.confidence} />
            <p className="muted small">confidence</p>
          </div>
        </div>
      )}
    </section>
  )
}

export function PlayerPage() {
  const { id = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const { season, week, resolving, error: weekError } = useWeekSelection(params)
  const validId = UUID.test(id)

  const player = useQuery({
    queryKey: ['player', id],
    queryFn: () => fetchPlayer(id),
    enabled: validId,
  })

  const position = player.data && isPosition(player.data.position) ? player.data.position : null
  // The same query the rankings screen runs, so arriving from there needs no second request.
  const rankings = useQuery({
    queryKey: ['start-sit', season, week, position],
    queryFn: () => fetchStartSit(season!, week!, position!),
    enabled: season !== null && week !== null && position !== null,
  })

  const index = rankings.data?.findIndex((row) => row.playerId === id) ?? -1
  const recommendation = index >= 0 ? rankings.data![index] : null
  const ready = season !== null && week !== null

  function update(next: { season?: number; week?: number }) {
    setParams({ season: String(next.season ?? season), week: String(next.week ?? week) })
  }

  const backTo = position
    ? `/?${new URLSearchParams({ ...(ready ? { season: String(season), week: String(week) } : {}), position })}`
    : '/'
  const backLabel = position ? `${POSITION_NAMES[position]} rankings` : 'Rankings'

  const notFound = !validId || (player.isError && player.error instanceof ApiError && player.error.status === 404)

  return (
    <div className="player-page">
      <div className="breadcrumb-row">
        <Link to={backTo} className="back-link">
          <ArrowLeft size={15} aria-hidden="true" />
          {backLabel}
        </Link>
        {ready && (
          <PeriodPickers
            season={season}
            week={week}
            onChange={update}
          />
        )}
      </div>

      {notFound && (
        <p className="card panel-status" role="alert">
          We couldn&apos;t find that player. Go back to the rankings and pick one from the list.
        </p>
      )}
      {!notFound && player.isError && (
        <p className="form-error" role="alert">
          {player.error.message}
        </p>
      )}
      {weekError && (
        <p className="form-error" role="alert">
          {weekError.message}
        </p>
      )}
      {!notFound && (player.isPending || resolving) && <p className="page-status">Loading…</p>}

      {player.isSuccess && (
        <>
          <Summary player={player.data} recommendation={recommendation} rank={index + 1} season={season} week={week} />

          {rankings.isError && (
            <p className="form-error" role="alert">
              {rankings.error.message}
            </p>
          )}
          {position === null && (
            <p className="card panel-status muted">Scores aren&apos;t produced for {player.data.position} players.</p>
          )}
          {rankings.isSuccess && recommendation === null && (
            <p className="card panel-status muted">
              {player.data.fullName} has no score for season {season}, week {week}. Players need at least one piece of
              data for a week to be scored; try another week.
            </p>
          )}
          {recommendation && season !== null && week !== null && (
            <div className="evidence-layout">
              <FactorEvidence recommendation={recommendation} />
              <ScoreContext recommendation={recommendation} season={season} week={week} />
            </div>
          )}
        </>
      )}
      <p className="muted small data-note">Scores describe the model&apos;s view of the week, not guaranteed outcomes.</p>
    </div>
  )
}
