import { X } from 'lucide-react'
import { useState } from 'react'
import type { PlayerDetail, PlayerSummary } from '../../api/types.ts'
import { PlayerPicker } from '../compare/PlayerPicker.tsx'
import { MAX_PER_SIDE } from './trade.ts'

export interface Chip {
  id: string
  player: PlayerDetail | null
  failed: boolean
}

export function SidePanel({
  title,
  hint,
  chips,
  excludeIds,
  onAdd,
  onRemove,
}: {
  title: string
  hint: string
  chips: Chip[]
  /** Everyone already on either side, so nobody is offered twice. */
  excludeIds: string[]
  onAdd: (player: PlayerSummary) => void
  onRemove: (id: string) => void
}) {
  const [adding, setAdding] = useState(false)
  const full = chips.length >= MAX_PER_SIDE

  return (
    <section className="card trade-side" aria-label={title}>
      <div className="trade-side-heading">
        <h2>{title}</h2>
        <p className="muted small">{hint}</p>
      </div>

      {chips.length > 0 && (
        <ul className="chip-list">
          {chips.map((chip) => (
            <li key={chip.id} className="chip">
              <span className="chip-text">
                <span className="chip-name">{chip.player?.fullName ?? (chip.failed ? 'Unknown player' : 'Loading…')}</span>
                {chip.player && (
                  <span className="muted small">
                    {chip.player.position}
                    {chip.player.team ? ` · ${chip.player.team}` : ''}
                  </span>
                )}
              </span>
              <button
                type="button"
                className="icon-button"
                aria-label={`Remove ${chip.player?.fullName ?? 'player'}`}
                onClick={() => onRemove(chip.id)}
              >
                <X size={16} aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}

      {adding && (
        <PlayerPicker
          label={`${title}: add a player`}
          onlyPosition={null}
          excludeIds={excludeIds}
          autoFocus
          onPick={(player) => {
            setAdding(false)
            onAdd(player)
          }}
        />
      )}

      {full ? (
        <p className="muted small">A side holds up to {MAX_PER_SIDE} players.</p>
      ) : (
        <button type="button" className="secondary-button" onClick={() => setAdding((value) => !value)}>
          {adding ? 'Cancel' : chips.length === 0 ? 'Add a player' : 'Add another player'}
        </button>
      )}
    </section>
  )
}
