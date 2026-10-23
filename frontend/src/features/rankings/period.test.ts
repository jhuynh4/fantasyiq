import { describe, expect, it } from 'vitest'
import {
  fallbackWeek,
  latestSeason,
  parseInteger,
  parsePosition,
  seasonChoices,
  seasonOptions,
  weekAfterSeasonChange,
  weekChoices,
} from './period.ts'

describe('latestSeason', () => {
  it('counts the months before September as still belonging to the season that started the year before', () => {
    expect(latestSeason(new Date(2027, 0, 15))).toBe(2026)
    expect(latestSeason(new Date(2026, 7, 31))).toBe(2025)
    expect(latestSeason(new Date(2026, 8, 1))).toBe(2026)
    expect(latestSeason(new Date(2026, 9, 20))).toBe(2026)
  })

  it('falls back to the end of the latest season', () => {
    expect(fallbackWeek(new Date(2026, 4, 1))).toEqual({ season: 2025, week: 18 })
  })
})

describe('parsing the address', () => {
  it('accepts only whole numbers inside the range', () => {
    expect(parseInteger('7', 1, 18)).toBe(7)
    expect(parseInteger('0', 1, 18)).toBeNull()
    expect(parseInteger('19', 1, 18)).toBeNull()
    expect(parseInteger('7.5', 1, 18)).toBeNull()
    expect(parseInteger('abc', 1, 18)).toBeNull()
    expect(parseInteger(null, 1, 18)).toBeNull()
  })

  it('defaults an unknown position to QB', () => {
    expect(parsePosition('WR')).toBe('WR')
    expect(parsePosition('K')).toBe('QB')
    expect(parsePosition(null)).toBe('QB')
  })
})

describe('seasonOptions', () => {
  it('offers the latest three seasons and keeps a selected older one available', () => {
    const now = new Date(2026, 9, 20)
    expect(seasonOptions(2026, now)).toEqual([2026, 2025, 2024])
    expect(seasonOptions(2020, now)).toEqual([2020, 2026, 2025, 2024])
  })
})

describe('picking from the weeks that have scores', () => {
  const periods = [
    { season: 2026, weeks: [7] },
    { season: 2025, weeks: [5, 6, 7, 8, 18] },
  ]

  it('offers only seasons with scores, and keeps a shown season that has none', () => {
    expect(seasonChoices(periods, 2025)).toEqual([2026, 2025])
    expect(seasonChoices(periods, 2024)).toEqual([2026, 2025, 2024])
  })

  it('falls back to the usual seasons and every week when the server has not said', () => {
    expect(seasonChoices(null, 2026, new Date(2026, 9, 20))).toEqual([2026, 2025, 2024])
    expect(weekChoices(null, 2026, 7)).toHaveLength(18)
    expect(weekChoices(null, 2026, 7).every((choice) => choice.hasScores)).toBe(true)
  })

  it('offers only the weeks of the season that have scores', () => {
    expect(weekChoices(periods, 2025, 6).map((choice) => choice.week)).toEqual([5, 6, 7, 8, 18])
    expect(weekChoices(periods, 2025, 6).every((choice) => choice.hasScores)).toBe(true)
  })

  it('keeps the week being shown selectable even when it has no scores, and says so', () => {
    const choices = weekChoices(periods, 2025, 12)

    expect(choices.map((choice) => choice.week)).toEqual([5, 6, 7, 8, 12, 18])
    expect(choices.find((choice) => choice.week === 12)?.hasScores).toBe(false)
    expect(choices.find((choice) => choice.week === 8)?.hasScores).toBe(true)
  })

  it('offers just the shown week for a season with no scores at all', () => {
    expect(weekChoices(periods, 2024, 3)).toEqual([{ week: 3, hasScores: false }])
  })

  it('keeps the week on a season change when the new season has it', () => {
    expect(weekAfterSeasonChange(periods, 2025, 7)).toBe(7)
  })

  it("moves to the new season's latest scored week when it lacks the current one", () => {
    expect(weekAfterSeasonChange(periods, 2025, 12)).toBe(18)
    expect(weekAfterSeasonChange(periods, 2026, 12)).toBe(7)
  })

  it('leaves the week alone when nothing is known about the season', () => {
    expect(weekAfterSeasonChange(periods, 2024, 12)).toBe(12)
    expect(weekAfterSeasonChange(null, 2025, 12)).toBe(12)
  })
})
