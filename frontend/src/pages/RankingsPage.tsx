import { useQuery } from '@tanstack/react-query'
import { ChevronDown } from 'lucide-react'
import type { KeyboardEvent, ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { apiFetch } from '../api/client.ts'
import { fetchStartSit } from '../api/recommendations.ts'
import type { CurrentWeek, Position } from '../api/types.ts'
import { FactorLegend, ConfidenceGuide } from '../features/rankings/RankingsKey.tsx'
import { RankingsTable } from '../features/rankings/RankingsTable.tsx'
import {
  POSITION_PLURALS,
  POSITIONS,
  WEEKS,
  fallbackWeek,
  parseInteger,
  parsePosition,
  seasonOptions,
} from '../features/rankings/period.ts'

function Picker({
  label,
  value,
  onChange,
  children,
}: {
  label: string
  value: number
  onChange: (value: number) => void
  children: ReactNode
}) {
  return (
    <label className="picker">
      <span className="visually-hidden">{label}</span>
      <select value={value} onChange={(event) => onChange(Number(event.target.value))}>
        {children}
      </select>
      <ChevronDown size={14} aria-hidden="true" />
    </label>
  )
}

export function RankingsPage() {
  const [params, setParams] = useSearchParams()
  const position = parsePosition(params.get('position'))
  const requestedSeason = parseInteger(params.get('season'), 2000, 2100)
  const requestedWeek = parseInteger(params.get('week'), 1, 18)

  const needsDefault = requestedSeason === null || requestedWeek === null
  const currentWeek = useQuery({
    queryKey: ['current-week'],
    queryFn: () => apiFetch<CurrentWeek>('/api/weeks/current'),
    enabled: needsDefault,
  })

  const fallback: CurrentWeek | null = needsDefault && currentWeek.isSuccess ? (currentWeek.data ?? fallbackWeek()) : null
  const season = requestedSeason ?? fallback?.season ?? null
  const week = requestedWeek ?? fallback?.week ?? null

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
  const resolving = needsDefault && currentWeek.isPending
  const ready = season !== null && week !== null
  const loading = resolving || (ready && rankings.isPending)

  return (
    <div className="rankings">
      <div className="page-heading">
        <div className="title-row">
          <h1 className="page-title">Weekly rankings</h1>
          {ready && (
            <div className="pickers">
              <Picker label="Season" value={season} onChange={(value) => update({ season: value })}>
                {seasonOptions(season).map((option) => (
                  <option key={option} value={option}>
                    Season {option}
                  </option>
                ))}
              </Picker>
              <Picker label="Week" value={week} onChange={(value) => update({ week: value })}>
                {WEEKS.map((option) => (
                  <option key={option} value={option}>
                    Week {option}
                  </option>
                ))}
              </Picker>
            </div>
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
          {currentWeek.isError && (
            <p className="form-error panel-status" role="alert">
              {currentWeek.error.message}
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
              <RankingsTable key={`${season}-${week}-${position}`} rows={rows} />
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
