import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { searchPlayers } from '../../api/players.ts'
import type { PlayerSummary, Position } from '../../api/types.ts'
import { POSITION_PLURALS, isPosition } from '../rankings/period.ts'

const MIN_QUERY_LENGTH = 2
const TYPING_PAUSE_MS = 250

/**
 * Search by name. Only positions the model scores are offered, and when `onlyPosition` is given only
 * that one: scores are built to compare players at the same position.
 */
export function PlayerPicker({
  label,
  onlyPosition,
  excludeIds,
  onPick,
  autoFocus = false,
}: {
  label: string
  onlyPosition: Position | null
  excludeIds: string[]
  onPick: (player: PlayerSummary) => void
  autoFocus?: boolean
}) {
  const [text, setText] = useState('')
  const [query, setQuery] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setQuery(text.trim()), TYPING_PAUSE_MS)
    return () => clearTimeout(timer)
  }, [text])

  const search = useQuery({
    queryKey: ['player-search', query],
    queryFn: () => searchPlayers(query),
    enabled: query.length >= MIN_QUERY_LENGTH,
  })

  const results = (search.data ?? []).filter(
    (player) => isPosition(player.position) && (onlyPosition === null || player.position === onlyPosition) && !excludeIds.includes(player.id),
  )
  const searching = query.length >= MIN_QUERY_LENGTH

  return (
    <div className="picker-box">
      <label className="search-field">
        <Search size={14} aria-hidden="true" />
        <span className="visually-hidden">{label}</span>
        <input
          type="search"
          placeholder="Search by name"
          autoComplete="off"
          autoFocus={autoFocus}
          value={text}
          onChange={(event) => setText(event.target.value)}
        />
      </label>
      {onlyPosition && <p className="muted small">Showing {POSITION_PLURALS[onlyPosition].toLowerCase()} only.</p>}
      {searching && search.isPending && <p className="muted small">Searching…</p>}
      {search.isError && (
        <p className="form-error" role="alert">
          {search.error.message}
        </p>
      )}
      {search.isSuccess && results.length === 0 && <p className="muted small">No matching players.</p>}
      {results.length > 0 && (
        <ul className="search-results" aria-label={`${label} results`}>
          {results.map((player) => (
            <li key={player.id}>
              <button type="button" onClick={() => onPick(player)}>
                <span className="result-name">{player.fullName}</span>
                <span className="muted small">
                  {player.position}
                  {player.team ? ` · ${player.team}` : ''}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
