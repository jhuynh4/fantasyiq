import { describe, expect, it } from 'vitest'
import type { TradeAnalysis, TradePlayerValue } from '../../api/types.ts'
import { MAX_PER_SIDE, formatValue, outcomeOf, parseIds, sameSelection } from './trade.ts'

const A = '11111111-1111-4111-8111-111111111111'
const B = '22222222-2222-4222-8222-222222222222'

function analysis(valueDelta: number, a: TradePlayerValue[] = [], b: TradePlayerValue[] = []): TradeAnalysis {
  return { sideA: { players: a, totalValue: 0 }, sideB: { players: b, totalValue: 0 }, valueDelta }
}

describe('parseIds', () => {
  it('keeps valid ids once each and ignores anything else', () => {
    expect(parseIds(`${A},${B},${A},not-an-id,`)).toEqual([A, B])
    expect(parseIds(null)).toEqual([])
    expect(parseIds('')).toEqual([])
  })

  it('stops at what a side can hold', () => {
    const many = Array.from({ length: 9 }, (_, index) => `00000000-0000-4000-8000-00000000000${index}`).join(',')
    expect(parseIds(many)).toHaveLength(MAX_PER_SIDE)
  })
})

describe('sameSelection', () => {
  it('ignores order', () => {
    expect(sameSelection([A, B], [B, A])).toBe(true)
    expect(sameSelection([A], [A, B])).toBe(false)
    expect(sameSelection([A], [B])).toBe(false)
  })
})

describe('outcomeOf', () => {
  it('reads the server delta (give minus get) from the side you receive', () => {
    expect(outcomeOf(analysis(-10)).advantage).toBe(10)
    expect(outcomeOf(analysis(-10)).headline).toBe('The side you get is worth 10.00 more points of value.')
    expect(outcomeOf(analysis(3.456)).headline).toBe('The side you give is worth 3.46 more points of value.')
  })

  it('calls a negligible difference level', () => {
    expect(outcomeOf(analysis(0.001)).headline).toBe('The two sides are worth the same.')
  })
})

describe('formatValue', () => {
  it('signs values, and shows zero without a sign', () => {
    expect(formatValue(3.91)).toBe('+3.91')
    expect(formatValue(-23.49)).toBe('−23.49')
    expect(formatValue(0.001)).toBe('0.00')
  })
})
