import { describe, expect, it } from 'vitest'
import { formatEta, formatKm, stepIndex, timeAgo } from './format.ts'

describe('timeAgo', () => {
  const now = Date.parse('2026-01-01T12:00:00Z')

  it('says "never" without a timestamp', () => {
    expect(timeAgo(null, now)).toBe('never')
  })

  it('uses seconds, minutes and hours', () => {
    expect(timeAgo('2026-01-01T11:59:58Z', now)).toBe('just now')
    expect(timeAgo('2026-01-01T11:59:30Z', now)).toBe('30 s ago')
    expect(timeAgo('2026-01-01T11:57:00Z', now)).toBe('3 min ago')
    expect(timeAgo('2026-01-01T09:00:00Z', now)).toBe('3 h ago')
  })

  it('never goes negative when the server clock is slightly ahead', () => {
    expect(timeAgo('2026-01-01T12:00:03Z', now)).toBe('just now')
  })
})

describe('formatKm', () => {
  it('handles unknown, sub-kilometre and long distances', () => {
    expect(formatKm(null)).toBe('—')
    expect(formatKm(0.25)).toBe('250 m')
    expect(formatKm(5.449)).toBe('5.4 km')
  })
})

describe('formatEta', () => {
  it('handles unknown, tiny and normal values', () => {
    expect(formatEta(null)).toBe('—')
    expect(formatEta(0.4)).toBe('under a minute')
    expect(formatEta(2.6)).toBe('3 min')
  })
})

describe('stepIndex', () => {
  it('orders the delivery stages', () => {
    expect(stepIndex('PENDING')).toBe(0)
    expect(stepIndex('DELIVERED')).toBe(3)
  })
})
