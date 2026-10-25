import { http, HttpResponse } from 'msw'
import type { Card, CardDraft, EnrichmentStatus, Me, PageResponse } from '../api/types'

export const me: Me = {
  id: 'u1',
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  preferences: { theme: 'SYSTEM', language: 'en', timezone: 'Europe/Warsaw' },
}

let seq = 0
export function card(overrides: Partial<Card> = {}): Card {
  seq += 1
  return {
    id: overrides.id ?? `c${seq}`,
    version: 0,
    title: `Card ${seq}`,
    description: 'A short description.',
    itemType: 'PLACE',
    venueName: null,
    address: null,
    city: 'Warszawa',
    country: 'Poland',
    latitude: null,
    longitude: null,
    creator: null,
    releaseYear: null,
    sourceUrl: null,
    price: null,
    eventStartDate: null,
    eventEndDate: null,
    eventDatesSource: null,
    expiresOn: null,
    expirationSource: null,
    expired: false,
    openingHoursNote: null,
    seasons: [],
    cover: null,
    enrichment: null,
    originalInput: null,
    collection: {
      status: 'ACTIVE',
      savedAt: '2026-10-25T10:00:00Z',
      visitedAt: null,
      removedAt: null,
      personalNote: null,
    },
    discovery: null,
    createdAt: '2026-10-25T10:00:00Z',
    updatedAt: '2026-10-25T10:00:00Z',
    ...overrides,
  }
}

export function page<T>(items: T[], hasNext = false): PageResponse<T> {
  return { items, page: 0, size: 24, totalItems: items.length, totalPages: 1, hasNext }
}

export const aiOff: EnrichmentStatus = {
  aiAvailable: false,
  provider: 'none',
  model: null,
  imageProviders: ['PAGE', 'WIKIMEDIA'],
  message: 'AI enrichment is not configured (set GEMINI_API_KEY or ANTHROPIC_API_KEY).',
}

export const aiOn: EnrichmentStatus = {
  ...aiOff,
  aiAvailable: true,
  provider: 'anthropic',
  model: 'claude-opus-5-5',
  message: 'AI enrichment is available.',
}

export function draft(overrides: Partial<CardDraft> = {}): CardDraft {
  return {
    mode: 'AI',
    card: {
      title: 'Ogród Botaniczny w Powsinie',
      description: 'A large botanical garden in southern Warsaw.',
      itemType: 'PLACE',
      city: 'Warszawa',
      country: 'Poland',
      priceMin: 15,
      currency: 'PLN',
      priceSource: 'SOURCE',
      seasons: ['SPRING', 'AUTUMN'],
      sourceUrl: 'https://www.ogrod-powsin.pl/',
    },
    fieldSources: { title: 'USER', description: 'AI', price: 'SOURCE', seasons: 'AI', city: 'AI' },
    suggestions: { title: 'Ogród Botaniczny PAN w Powsinie' },
    confidence: { overall: 'HIGH', identification: 'HIGH', dates: 'LOW', price: 'HIGH', location: 'HIGH' },
    unverifiedFields: ['openingHoursNote'],
    missingFields: [],
    sources: [{ url: 'https://www.ogrod-powsin.pl/', title: 'Ogród Botaniczny' }],
    coverCandidates: [],
    possibleDuplicates: [],
    warnings: ['Not verified: winter opening hours'],
    originalInput: 'Ogród Botaniczny w Powsinie',
    enrichment: { mode: 'AI', provider: 'anthropic' },
    ...overrides,
  }
}

/** Handlers for a signed-in user with the given listings. */
export function baseHandlers(
  opts: { collection?: Card[]; discover?: Card[]; expired?: Card[]; status?: EnrichmentStatus } = {},
) {
  return [
    http.get('/api/v1/auth/csrf', () => {
      document.cookie = 'XSRF-TOKEN=test-token; path=/'
      return new HttpResponse(null, { status: 204 })
    }),
    http.get('/api/v1/me', () => HttpResponse.json(me)),
    http.get('/api/v1/enrichment/status', () => HttpResponse.json(opts.status ?? aiOff)),
    http.get('/api/v1/collection', () => HttpResponse.json(page(opts.collection ?? []))),
    http.get('/api/v1/discover', () => HttpResponse.json(page(opts.discover ?? []))),
    http.get('/api/v1/expired', () => HttpResponse.json(page(opts.expired ?? []))),
  ]
}
