import { useQuery } from '@tanstack/react-query'
import type { KeyboardEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { fetchStartSit } from '../api/recommendations.ts'
import type { Position } from '../api/types.ts'
import { PeriodPickers } from '../components/PeriodPickers.tsx'
import { FactorLegend, ConfidenceGuide } from '../features/rankings/RankingsKey.tsx'
import { RankingsTable } from '../features/rankings/RankingsTable.tsx'
import { POSITION_PLURALS, POSITIONS, parsePosition } from '../features/rankings/period.ts'
import { useWeekSelection } from '../features/rankings/useWeekSelection.ts'

export function RankingsPage() {
  const [params, setParams] = useSearchParams()
  const position = parsePosition(params.get('position'))
  const { season, week, resolving, error: weekError } = useWeekSelection(params)

  const rankings = useQuery({
    queryKey: ['start-sit', season, week, position],
    queryFn: () => fetchStartSit(season!, week!, position),
    enabled: season !== null && week !== null,
  })

  function update(next: { season?: number; week?: number; position?: Position }) {
    setParams({
      season: String(next.season ?? season),
      week: String(next.week ?? week),
      position: next.position ?? position,
    })
  }

  function onTabKeyDown(event: KeyboardEvent, index: number) {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (step === 0) return
    event.preventDefault()
    const next = POSITIONS[(index + step + POSITIONS.length) % POSITIONS.length]
    update({ position: next })
    document.getElementById(`tab-${next}`)?.focus()
  }

  const rows = rankings.data ?? []
  const ready = season !== null && week !== null
  const loading = resolving || (ready && rankings.isPending)

  return (
    <div className="rankings">
      <div className="page-heading">
        <div className="title-row">
          <h1 className="page-title">Weekly rankings</h1>
          {ready && (
            <PeriodPickers
              season={season}
              week={week}
              onChange={update}
            />
          )}
        </div>
        <p className="muted">A clear view of player value, with every contribution in the open.</p>
      </div>

      <section className="card rankings-card" aria-label="Position rankings">
        <div className="tabs" role="tablist" aria-label="Position">
          {POSITIONS.map((option, index) => (
            <button
              key={option}
              id={`tab-${option}`}
              type="button"
              role="tab"
              aria-selected={option === position}
              aria-controls="rankings-panel"
              tabIndex={option === position ? 0 : -1}
              className="tab"
              onClick={() => update({ position: option })}
              onKeyDown={(event) => onTabKeyDown(event, index)}
              disabled={!ready}
            >
              {option}
            </button>
          ))}
        </div>

        <div id="rankings-panel" role="tabpanel" aria-labelledby={`tab-${position}`}>
          <div className="table-context">
            <strong>
              {POSITION_PLURALS[position]}
              {rankings.isSuccess ? ` · ${rows.length} ${rows.length === 1 ? 'player' : 'players'}` : ''}
            </strong>
            <span className="muted">Score ↓</span>
          </div>

          {loading && <p className="panel-status">Loading rankings…</p>}
          {weekError && (
            <p className="form-error panel-status" role="alert">
              {weekError.message}
            </p>
          )}
          {rankings.isError && (
            <p className="form-error panel-status" role="alert">
              {rankings.error.message}
            </p>
          )}
          {rankings.isSuccess && rows.length === 0 && (
            <p className="panel-status muted">
              No rankings have been generated for {POSITION_PLURALS[position].toLowerCase()} in season {season}, week{' '}
              {week}. Try another week or season.
            </p>
          )}
          {rows.length > 0 && (
            <>
              <RankingsTable
                key={`${season}-${week}-${position}`}
                rows={rows}
                linkQuery={`season=${season}&week=${week}`}
              />
              <FactorLegend />
            </>
          )}
        </div>
      </section>

      <ConfidenceGuide />
      <p className="muted small data-note">Scores describe the model's view of the week, not guaranteed outcomes.</p>
    </div>
  )
}
