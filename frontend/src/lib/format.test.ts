import { describe, expect, it } from 'vitest'
import { daysUntil, expiryLabel, formatDateRange, formatPrice, metaLine, parseDate } from './format'
import { card } from '../test/fixtures'

describe('format', () => {
  it('parses calendar dates without timezone shifts', () => {
    const d = parseDate('2026-12-24')
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 11, 24])
  })

  it('counts whole local days, also across a DST change', () => {
    expect(daysUntil('2026-10-26', new Date(2026, 9, 25, 23, 30))).toBe(1)
    expect(daysUntil('2026-03-30', new Date(2026, 2, 29, 0, 0))).toBe(1)
    expect(daysUntil('2026-10-20', new Date(2026, 9, 25))).toBe(-5)
  })

  it('labels expiry relative to today', () => {
    const today = new Date(2026, 9, 25, 12)
    expect(expiryLabel({ expiresOn: '2026-10-25', expired: false }, today)).toBe('Last day today')
    expect(expiryLabel({ expiresOn: '2026-10-26', expired: false }, today)).toBe('Ends tomorrow')
    expect(expiryLabel({ expiresOn: '2026-11-01', expired: false }, today)).toBe('Ends in 7 days')
    expect(expiryLabel({ expiresOn: '2027-01-17', expired: false }, today)).toBe('Until 17 Jan 2027')
    expect(expiryLabel({ expiresOn: '2026-09-28', expired: true }, today)).toBe('Ended 28 Sept 2026')
    expect(expiryLabel({ expiresOn: null, expired: false }, today)).toBeNull()
  })

  it('formats prices, ranges and free entry', () => {
    expect(formatPrice({ min: 0, max: null, currency: 'PLN', note: null, source: 'USER' })).toBe('Free')
    expect(formatPrice({ min: 15, max: null, currency: 'PLN', note: null, source: 'USER' })).toMatch(
      /PLN\s?15/,
    )
    expect(formatPrice({ min: 25, max: 35.5, currency: 'EUR', note: null, source: 'USER' })).toMatch(
      /^€25\.00\s?–\s?€?35\.50$/,
    )
    expect(formatPrice({ min: 20, max: 25, currency: 'PLN', note: null, source: 'USER' })).toMatch(
      /^PLN\s?20\s?–\s?25$/,
    )
    expect(formatPrice(null)).toBe('')
  })

  it('formats date ranges compactly', () => {
    expect(formatDateRange('2026-11-20', '2026-12-23')).toBe('20 Nov – 23 Dec 2026')
    expect(formatDateRange('2026-10-03', '2027-01-17')).toBe('3 Oct 2026 – 17 Jan 2027')
    expect(formatDateRange(null, '2027-01-17')).toBe('until 17 Jan 2027')
    expect(formatDateRange('2026-12-05', '2026-12-05')).toBe('5 Dec 2026')
  })

  it('prioritises information by item type', () => {
    expect(metaLine(card({ itemType: 'FILM', creator: 'Wim Wenders', releaseYear: 2023 }))).toBe(
      'Wim Wenders · 2023',
    )
    expect(metaLine(card({ itemType: 'PLACE', venueName: null, city: 'Kraków', price: null }))).toBe('Kraków')
  })
})
