import { ChevronDown } from 'lucide-react'
import type { ReactNode } from 'react'
import { WEEKS, seasonOptions } from '../features/rankings/period.ts'

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

export function PeriodPickers({
  season,
  week,
  onSeasonChange,
  onWeekChange,
}: {
  season: number
  week: number
  onSeasonChange: (season: number) => void
  onWeekChange: (week: number) => void
}) {
  return (
    <div className="pickers">
      <Picker label="Season" value={season} onChange={onSeasonChange}>
        {seasonOptions(season).map((option) => (
          <option key={option} value={option}>
            Season {option}
          </option>
        ))}
      </Picker>
      <Picker label="Week" value={week} onChange={onWeekChange}>
        {WEEKS.map((option) => (
          <option key={option} value={option}>
            Week {option}
          </option>
        ))}
      </Picker>
    </div>
  )
}
