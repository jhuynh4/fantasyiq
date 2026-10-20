import { ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { Confidence, StartSitRecommendation } from '../../api/types.ts'
import { FactorShape } from './FactorShape.tsx'
import { POSITION_NAMES } from './period.ts'
import { positiveTotal } from './shape.ts'

export function ConfidenceBadge({ level }: { level: Confidence }) {
  return <span className={`badge badge-${level.toLowerCase()}`}>{level}</span>
}

/** A position has hundreds of scored players, most of them irrelevant to a start/sit call; the top of the list is what gets read. */
export const INITIAL_ROWS = 50

/**
 * `linkQuery` carries the season and week to the player page. Remount the table (change its key)
 * when the list it shows changes, so "show all" doesn't carry over.
 */
export function RankingsTable({ rows, linkQuery }: { rows: StartSitRecommendation[]; linkQuery: string }) {
  const [showAll, setShowAll] = useState(false)
  // The scale comes from the whole list, so a row's bar means the same thing before and after "show all".
  const maxPositive = rows.reduce((max, row) => Math.max(max, positiveTotal(row.factors)), 0)
  const visible = showAll ? rows : rows.slice(0, INITIAL_ROWS)

  return (
    <>
      <table className="rankings-table">
        <thead>
          <tr>
            <th scope="col">Rank</th>
            <th scope="col">Player</th>
            <th scope="col">Team</th>
            <th scope="col">Score</th>
            <th scope="col">Confidence</th>
            <th scope="col">Factor shape</th>
          </tr>
        </thead>
        <tbody>
          {visible.map((row, index) => (
            <tr key={row.playerId}>
              <td className="cell-rank mono">{String(index + 1).padStart(2, '0')}</td>
              <td className="cell-player">
                <Link className="player-name row-link" to={`/players/${row.playerId}?${linkQuery}`}>
                  {row.playerName}
                </Link>
                <span className="player-meta">
                  <span className="meta-position">{POSITION_NAMES[row.position]}</span>
                  <span className="meta-compact">
                    {row.position}
                    {row.team ? ` · ${row.team}` : ''}
                  </span>
                </span>
              </td>
              <td className="cell-team">{row.team ?? '—'}</td>
              <td className="cell-score mono">{row.score.toFixed(2)}</td>
              <td className="cell-confidence">
                <ConfidenceBadge level={row.confidence} />
              </td>
              <td className="cell-shape">
                <FactorShape factors={row.factors} maxPositive={maxPositive} />
              </td>
              <td className="cell-chevron" aria-hidden="true">
                <ChevronRight size={15} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length > visible.length && (
        <button type="button" className="show-all" onClick={() => setShowAll(true)}>
          Show all {rows.length} players
        </button>
      )}
    </>
  )
}
