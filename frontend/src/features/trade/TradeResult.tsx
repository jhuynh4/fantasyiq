import type { TradeAnalysis, TradePlayerValue, TradeSide } from '../../api/types.ts'
import { formatValue, formatPlain, outcomeOf } from './trade.ts'
import { labelFor, sortFactors } from '../rankings/factors.ts'

function PlayerRow({ player }: { player: TradePlayerValue }) {
  const hasData = player.score !== null && player.valueAboveReplacement !== null
  return (
    <li className="trade-player">
      <div className="trade-player-head">
        <div>
          <p className="trade-player-name">{player.playerName}</p>
          <p className="muted small">{player.position}</p>
        </div>
        <p className={`trade-value mono ${hasData && player.valueAboveReplacement! < 0 ? 'negative' : 'positive'}`}>
          {hasData ? formatValue(player.valueAboveReplacement!) : '—'}
        </p>
      </div>

      {hasData ? (
        <p className="muted small mono">
          Score {formatPlain(player.score!)} · replacement level{' '}
          {player.replacementLevel !== null ? formatPlain(player.replacementLevel) : 'n/a'} · value {formatValue(player.valueAboveReplacement!)}
        </p>
      ) : (
        <p className="muted small">The model has no data on this player, so they count as zero.</p>
      )}

      {player.factors.length > 0 && (
        <ul className="factor-chips">
          {sortFactors(player.factors).map((factor) => (
            <li key={factor.factorType} title={factor.narrative}>
              {labelFor(factor.factorType)}{' '}
              <span className={`mono ${factor.contribution < 0 ? 'negative' : 'positive'}`}>
                {formatValue(factor.contribution)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}

function SideResult({ title, side }: { title: string; side: TradeSide }) {
  return (
    <section className="card trade-result-side" aria-label={`${title} result`}>
      <div className="trade-result-heading">
        <h3>{title}</h3>
        <p className="trade-total mono">{formatValue(side.totalValue)}</p>
      </div>
      <ul className="trade-players">
        {side.players.map((player) => (
          <PlayerRow key={player.playerId} player={player} />
        ))}
      </ul>
    </section>
  )
}

export function TradeResult({ analysis }: { analysis: TradeAnalysis }) {
  const outcome = outcomeOf(analysis)

  return (
    <div className="trade-result" aria-live="polite">
      <div className="gap-box">
        <p className="gap-value mono">{formatValue(outcome.advantage)}</p>
        <div>
          <p className="gap-title">{outcome.headline}</p>
          <p className="muted small">
            You get {formatPlain(analysis.sideB.totalValue)} and give {formatPlain(analysis.sideA.totalValue)}, a difference of{' '}
            {formatPlain(outcome.advantage)}. That is a gap in modeled value, not advice on whether to accept.
          </p>
        </div>
      </div>

      <div className="trade-sides">
        <SideResult title="You give" side={analysis.sideA} />
        <SideResult title="You get" side={analysis.sideB} />
      </div>
    </div>
  )
}
