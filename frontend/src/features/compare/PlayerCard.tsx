import { useState } from 'react'
import type { PlayerDetail, PlayerSummary, Position, StartSitRecommendation } from '../../api/types.ts'
import { ConfidenceBadge } from '../rankings/RankingsTable.tsx'
import { PlayerPicker } from './PlayerPicker.tsx'

export interface CardData {
  player: PlayerDetail | null
  loading: boolean
  /** The player's score that week, or null when they have none. */
  recommendation: StartSitRecommendation | null
  rank: number | null
  scoreLoaded: boolean
}

export function PlayerCard({
  slot,
  heading,
  highlighted,
  data,
  onlyPosition,
  excludeId,
  onPick,
}: {
  slot: string
  heading: string
  highlighted: boolean
  data: CardData
  onlyPosition: Position | null
  excludeId: string | null
  onPick: (player: PlayerSummary) => void
}) {
  const [changing, setChanging] = useState(false)
  const { player, recommendation } = data
  const choosing = player === null && !data.loading
  const meta = player
    ? [player.position, player.team, player.jerseyNumber !== null ? `#${player.jerseyNumber}` : null].filter(Boolean).join(' · ')
    : ''

  return (
    <section className={`card compare-card ${highlighted ? 'compare-card-lead' : ''}`} aria-label={slot}>
      <p className={`eyebrow-small ${highlighted ? 'accent' : ''}`}>{heading}</p>

      {data.loading && <p className="muted">Loading…</p>}

      {(choosing || changing) && (
        <PlayerPicker
          label={slot}
          onlyPosition={onlyPosition}
          excludeId={excludeId}
          autoFocus={changing}
          onPick={(picked) => {
            setChanging(false)
            onPick(picked)
          }}
        />
      )}

      {player && !changing && (
        <>
          <div className="compare-identity">
            <h2>{player.fullName}</h2>
            <p className="muted small">{meta}</p>
          </div>
          {recommendation ? (
            <>
              <div className="compare-score">
                <span className="mono">{recommendation.score.toFixed(2)}</span>
                <span className="muted small">
                  #{data.rank} {recommendation.position}
                </span>
              </div>
              <ConfidenceBadge level={recommendation.confidence} />
            </>
          ) : (
            data.scoreLoaded && <p className="muted small">No score for this week.</p>
          )}
        </>
      )}

      {player && (
        <button type="button" className="link-button change-player" onClick={() => setChanging((value) => !value)}>
          {changing ? 'Cancel' : 'Change player'}
        </button>
      )}
    </section>
  )
}
