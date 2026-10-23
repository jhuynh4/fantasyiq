import { ChevronDown } from 'lucide-react'
import type { ReactNode } from 'react'
import { seasonChoices, weekAfterSeasonChange, weekChoices } from '../features/rankings/period.ts'
import { useAvailablePeriods } from '../features/rankings/usePeriods.ts'

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

/**
 * Offers the seasons and weeks that have scores. A change is reported as one season-and-week pair, so
 * switching to a season that has no scores for the current week can move the week in the same step.
 */
export function PeriodPickers({
  season,
  week,
  onChange,
}: {
  season: number
  week: number
  onChange: (next: { season: number; week: number }) => void
}) {
  const { periods } = useAvailablePeriods()

  return (
    <div className="pickers">
      <Picker
        label="Season"
        value={season}
        onChange={(value) => onChange({ season: value, week: weekAfterSeasonChange(periods, value, week) })}
      >
        {seasonChoices(periods, season).map((option) => (
          <option key={option} value={option}>
            Season {option}
          </option>
        ))}
      </Picker>
      <Picker label="Week" value={week} onChange={(value) => onChange({ season, week: value })}>
        {weekChoices(periods, season, week).map((option) => (
          <option key={option.week} value={option.week}>
            Week {option.week}
            {option.hasScores ? '' : ' · no scores yet'}
          </option>
        ))}
      </Picker>
    </div>
  )
}
