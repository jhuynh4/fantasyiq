import { describe, expect, it } from 'vitest'
import { fallbackWeek, latestSeason, parseInteger, parsePosition, seasonOptions } from './period.ts'

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
