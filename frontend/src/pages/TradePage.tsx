import { useQueries, useQuery } from '@tanstack/react-query'
import { Info } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { fetchPlayer } from '../api/players.ts'
import { analyzeTrade } from '../api/trades.ts'
import { type Chip, SidePanel } from '../features/trade/SidePanel.tsx'
import { TradeResult } from '../features/trade/TradeResult.tsx'
import { MAX_PER_SIDE, parseIds, sameSelection } from '../features/trade/trade.ts'

function useChips(ids: string[]): Chip[] {
  const players = useQueries({
    queries: ids.map((id) => ({ queryKey: ['player', id], queryFn: () => fetchPlayer(id) })),
  })
  return ids.map((id, index) => ({ id, player: players[index].data ?? null, failed: players[index].isError }))
}

export function TradePage() {
  const [params, setParams] = useSearchParams()
  const give = parseIds(params.get('give'))
  const get = parseIds(params.get('get'))
  const giveChips = useChips(give)
  const getChips = useChips(get)

  // What the last "Analyze" ran on. A link with both sides filled in analyzes straight away.
  const [submitted, setSubmitted] = useState<{ give: string[]; get: string[] } | null>(() =>
    give.length > 0 && get.length > 0 ? { give, get } : null,
  )

  const analysis = useQuery({
    queryKey: ['trade', submitted?.give, submitted?.get],
    queryFn: () => analyzeTrade(submitted!.give, submitted!.get),
    enabled: submitted !== null,
    staleTime: Infinity,
  })

  function setSide(key: 'give' | 'get', ids: string[]) {
    const next = new URLSearchParams(params)
    if (ids.length > 0) next.set(key, ids.join(','))
    else next.delete(key)
    setParams(next)
  }

  const current = submitted !== null && sameSelection(submitted.give, give) && sameSelection(submitted.get, get)
  const canAnalyze = give.length > 0 && get.length > 0
  const everyone = [...give, ...get]

  return (
    <div className="trade-page">
      <div className="page-heading">
        <h1 className="page-title">Trade analyzer</h1>
        <p className="muted">
          Add the players on each side. The model compares what each side is worth above a replacement-level player at the
          same position over the rest of the season.
        </p>
      </div>

      <div className="trade-panels">
        <SidePanel
          title="You give"
          hint="The players you would trade away."
          chips={giveChips}
          excludeIds={everyone}
          onAdd={(player) => setSide('give', [...give, player.id])}
          onRemove={(id) => setSide('give', give.filter((other) => other !== id))}
        />
        <SidePanel
          title="You get"
          hint="The players you would receive."
          chips={getChips}
          excludeIds={everyone}
          onAdd={(player) => setSide('get', [...get, player.id])}
          onRemove={(id) => setSide('get', get.filter((other) => other !== id))}
        />
      </div>

      <div className="analyze-row">
        <button
          type="button"
          className="primary-button"
          disabled={!canAnalyze || (current && analysis.isFetching)}
          onClick={() => setSubmitted({ give, get })}
        >
          Analyze trade
        </button>
        {!canAnalyze && <p className="muted small">Add at least one player to each side.</p>}
        {canAnalyze && submitted !== null && !current && (
          <p className="muted small">You changed the players. Analyze again to update the result.</p>
        )}
      </div>

      {current && analysis.isPending && (
        <p className="card panel-status" role="status">
          Analyzing the trade… this can take a few seconds, because the model scores every player at each position to find
          what a replacement-level player is worth.
        </p>
      )}
      {current && analysis.isError && (
        <p className="form-error" role="alert">
          {analysis.error.message}
        </p>
      )}
      {current && analysis.isSuccess && <TradeResult analysis={analysis.data} />}

      <section className="card how-it-works" aria-labelledby="how-heading">
        <h2 id="how-heading">
          <Info size={15} aria-hidden="true" />
          How a player&apos;s value is worked out
        </h2>
        <ul>
          <li>
            A player&apos;s score uses only what does not depend on one particular game: their most recent game, their usage
            trend over the last few games, and their current injury status. Matchup, betting lines and weather are left
            out because they describe a single week.
          </li>
          <li>
            Value is the score minus a replacement level, the score of the player at that position who is just good enough
            to be started (12th quarterback or tight end, 24th running back or wide receiver). That makes players at
            different positions comparable, and it means a player below replacement level has negative value.
          </li>
          <li>Up to {MAX_PER_SIDE} players per side. Values are not a prediction of fantasy points.</li>
        </ul>
      </section>
    </div>
  )
}
