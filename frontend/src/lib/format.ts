import type { Card, ItemType, Season } from '../api/types'

const DATE = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
const DATE_NO_YEAR = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' })

/** Parses an ISO calendar date (YYYY-MM-DD) as a local date, never shifting it across timezones. */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1)
}

export function formatDate(iso: string | null | undefined, withYear = true): string {
  if (!iso) return ''
  return (withYear ? DATE : DATE_NO_YEAR).format(parseDate(iso))
}

export function formatDateRange(start: string | null, end: string | null): string {
  if (start && end) {
    if (start === end) return formatDate(start)
    const sameYear = start.slice(0, 4) === end.slice(0, 4)
    return `${formatDate(start, !sameYear)} – ${formatDate(end)}`
  }
  if (end) return `until ${formatDate(end)}`
  if (start) return `from ${formatDate(start)}`
  return ''
}

export function formatPrice(price: Card['price']): string {
  if (!price) return ''
  const fmt = (v: number) =>
    new Intl.NumberFormat('en-GB', {
      style: 'currency',
      currency: price.currency,
      minimumFractionDigits: Number.isInteger(v) ? 0 : 2,
      maximumFractionDigits: 2,
    }).format(v)
  if (price.min === 0 && (price.max === null || price.max === 0)) return 'Free'
  return price.max !== null && price.max !== price.min
    ? `${fmt(price.min)}–${fmt(price.max)}`
    : fmt(price.min)
}

/** Whole days from `today` (local) to the ISO date; negative when in the past. */
export function daysUntil(iso: string, today = new Date()): number {
  const t = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return Math.round((parseDate(iso).getTime() - t.getTime()) / 86_400_000)
}

export function expiryLabel(card: Pick<Card, 'expiresOn' | 'expired'>, today = new Date()): string | null {
  if (!card.expiresOn) return null
  if (card.expired) return `Ended ${formatDate(card.expiresOn)}`
  const days = daysUntil(card.expiresOn, today)
  if (days <= 0) return 'Last day today'
  if (days === 1) return 'Ends tomorrow'
  if (days <= 14) return `Ends in ${days} days`
  return `Until ${formatDate(card.expiresOn)}`
}

export const SEASON_LABEL: Record<Season, string> = {
  SPRING: 'Spring',
  SUMMER: 'Summer',
  AUTUMN: 'Autumn',
  WINTER: 'Winter',
}

export const TYPE_LABEL: Record<ItemType, string> = {
  PLACE: 'Place',
  MUSEUM: 'Museum',
  EXHIBITION: 'Exhibition',
  PERFORMANCE: 'Performance',
  CONCERT: 'Concert',
  FESTIVAL: 'Festival',
  MARKET: 'Market',
  FILM: 'Film',
  RESTAURANT: 'Restaurant',
  ACTIVITY: 'Activity',
  EVENT: 'Event',
  OTHER: 'Other',
}

export function locationLine(card: Pick<Card, 'venueName' | 'city' | 'country'>): string {
  return [card.venueName, card.city].filter(Boolean).join(' · ') || card.country || ''
}

/** The one-line summary under a card title, prioritised by item type. */
export function metaLine(card: Card): string {
  const price = formatPrice(card.price)
  switch (card.itemType) {
    case 'FILM':
      return [card.creator, card.releaseYear].filter(Boolean).join(' · ')
    case 'EXHIBITION':
    case 'PERFORMANCE':
    case 'CONCERT':
    case 'FESTIVAL':
    case 'MARKET':
    case 'EVENT':
      return [card.venueName ?? card.city, formatDateRange(card.eventStartDate, card.eventEndDate), price]
        .filter(Boolean)
        .join(' · ')
    default:
      return [locationLine(card), price].filter(Boolean).join(' · ')
  }
}

export function hostOf(url: string | null | undefined): string {
  if (!url) return ''
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return ''
  }
}
