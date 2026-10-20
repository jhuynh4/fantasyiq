import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../api/client.ts'
import type { CurrentWeek } from '../api/types.ts'

export function RankingsPage() {
  const week = useQuery({
    queryKey: ['current-week'],
    queryFn: () => apiFetch<CurrentWeek>('/api/weeks/current'),
  })

  return (
    <section>
      <h1 className="page-title">Rankings</h1>
      {week.isPending && <p className="page-status">Loading…</p>}
      {week.isError && (
        <p className="form-error" role="alert">
          {week.error.message}
        </p>
      )}
      {week.isSuccess && week.data && (
        <p className="muted">
          Season {week.data.season}, week {week.data.week}
        </p>
      )}
      {week.isSuccess && !week.data && (
        <p className="muted">There is no game week in progress right now. Pick a week to explore.</p>
      )}
    </section>
  )
}
