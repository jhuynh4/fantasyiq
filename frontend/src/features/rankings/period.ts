import type { CurrentWeek, Position } from '../../api/types.ts'

export const POSITIONS: Position[] = ['QB', 'RB', 'WR', 'TE']
export const WEEKS = Array.from({ length: 18 }, (_, index) => index + 1)

export const POSITION_PLURALS: Record<Position, string> = {
  QB: 'Quarterbacks',
  RB: 'Running backs',
  WR: 'Wide receivers',
  TE: 'Tight ends',
}

/** A season is named for the year it starts, so before September the one that just ended is still "current". */
export function latestSeason(now: Date = new Date()): number {
  return now.getMonth() < 8 ? now.getFullYear() - 1 : now.getFullYear()
}

/** What to show when the server reports no game week in progress: the end of the latest season. */
export function fallbackWeek(now: Date = new Date()): CurrentWeek {
  return { season: latestSeason(now), week: 18 }
}

export function parseInteger(value: string | null, min: number, max: number): number | null {
  if (value === null || !/^\d+$/.test(value)) return null
  const parsed = Number(value)
  return parsed >= min && parsed <= max ? parsed : null
}

export function parsePosition(value: string | null): Position {
  return POSITIONS.find((position) => position === value) ?? 'QB'
}

/** The seasons offered in the picker: the latest few, plus whichever one is being shown. */
export function seasonOptions(selected: number, now: Date = new Date()): number[] {
  const latest = Math.max(latestSeason(now), selected)
  const seasons = [latest, latest - 1, latest - 2]
  return seasons.includes(selected) ? seasons : [selected, ...seasons]
}

export const POSITION_NAMES: Record<Position, string> = {
  QB: 'Quarterback',
  RB: 'Running back',
  WR: 'Wide receiver',
  TE: 'Tight end',
}

export function isPosition(value: string): value is Position {
  return POSITIONS.some((position) => position === value)
}
