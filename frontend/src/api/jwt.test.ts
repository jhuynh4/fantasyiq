import { describe, expect, it } from 'vitest'
import { decodeClaims } from './jwt.ts'

function tokenWith(payload: object): string {
  const encoded = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `header.${encoded}.signature`
}

describe('decodeClaims', () => {
  it('reads the email and role', () => {
    expect(decodeClaims(tokenWith({ email: 'coach@example.com', role: 'ADMIN' }))).toEqual({
      email: 'coach@example.com',
      role: 'ADMIN',
    })
  })

  it('handles non-ASCII characters in the payload', () => {
    const payload = btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify({ email: 'józef@example.com' }))))
    expect(decodeClaims(`h.${payload}.s`).email).toBe('józef@example.com')
  })

  it('returns empty claims for a missing or malformed token', () => {
    const empty = { email: null, role: null }
    expect(decodeClaims(null)).toEqual(empty)
    expect(decodeClaims('not-a-jwt')).toEqual(empty)
    expect(decodeClaims('a.%%%.c')).toEqual(empty)
  })
})
