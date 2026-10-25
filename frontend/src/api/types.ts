export const SEASONS = ['SPRING', 'SUMMER', 'AUTUMN', 'WINTER'] as const
export type Season = (typeof SEASONS)[number]
export type SeasonFilter = 'ALL' | Season | 'NON_SEASONAL'
export type SortOrder = 'RECENT' | 'ALPHABETICAL' | 'EVENT_DATE'

export const ITEM_TYPES = [
  'PLACE',
  'MUSEUM',
  'EXHIBITION',
  'PERFORMANCE',
  'CONCERT',
  'FESTIVAL',
  'MARKET',
  'FILM',
  'RESTAURANT',
  'ACTIVITY',
  'EVENT',
  'OTHER',
] as const
export type ItemType = (typeof ITEM_TYPES)[number]
export const TEMPORARY_TYPES: ReadonlySet<ItemType> = new Set([
  'EXHIBITION',
  'PERFORMANCE',
  'CONCERT',
  'FESTIVAL',
  'MARKET',
  'EVENT',
])

export type FactSource = 'USER' | 'SOURCE' | 'AI'
export type CoverSource = 'PAGE' | 'WIKIMEDIA' | 'UNSPLASH' | 'USER_URL' | 'UPLOAD'
export type Theme = 'DARK' | 'LIGHT' | 'SYSTEM'
export type ConfidenceLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Preferences {
  theme: Theme
  language: string
  timezone: string
}

export interface Me {
  id: string
  email: string
  displayName: string
  preferences: Preferences
}

export interface Price {
  min: number
  max: number | null
  currency: string
  note: string | null
  source: FactSource | null
}

export interface Cover {
  url: string
  source: CoverSource
  attribution: string | null
  attributionUrl: string | null
  license: string | null
  uploadId: string | null
}

export interface CollectionInfo {
  status: 'ACTIVE' | 'REMOVED'
  savedAt: string
  visitedAt: string | null
  removedAt: string | null
  personalNote: string | null
}

export interface DiscoveryInfo {
  status: 'PENDING' | 'SAVED' | 'DISMISSED'
  createdAt: string
  dismissedAt: string | null
  savedAt: string | null
}

export interface Card {
  id: string
  version: number
  title: string
  description: string | null
  itemType: ItemType
  venueName: string | null
  address: string | null
  city: string | null
  country: string | null
  latitude: number | null
  longitude: number | null
  creator: string | null
  releaseYear: number | null
  sourceUrl: string | null
  price: Price | null
  eventStartDate: string | null
  eventEndDate: string | null
  eventDatesSource: FactSource | null
  expiresOn: string | null
  expirationSource: FactSource | null
  expired: boolean
  openingHoursNote: string | null
  seasons: Season[]
  cover: Cover | null
  enrichment: Record<string, unknown> | null
  originalInput: string | null
  collection: CollectionInfo | null
  discovery: DiscoveryInfo | null
  createdAt: string
  updatedAt: string
}

export interface CoverInput {
  source: CoverSource
  url?: string | null
  uploadId?: string | null
  attribution?: string | null
  attributionUrl?: string | null
  license?: string | null
}

/** Mirrors the backend CardInput record. */
export interface CardInput {
  title: string
  description?: string | null
  itemType: ItemType
  venueName?: string | null
  address?: string | null
  city?: string | null
  country?: string | null
  latitude?: number | null
  longitude?: number | null
  creator?: string | null
  releaseYear?: number | null
  sourceUrl?: string | null
  priceMin?: number | null
  priceMax?: number | null
  currency?: string | null
  priceNote?: string | null
  priceSource?: FactSource | null
  eventStartDate?: string | null
  eventEndDate?: string | null
  eventDatesSource?: FactSource | null
  expiresOn?: string | null
  expirationSource?: FactSource | null
  openingHoursNote?: string | null
  seasons?: Season[]
  cover?: CoverInput | null
  version?: number
}

export type Destination = 'COLLECTION' | 'DISCOVER' | 'NONE'

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  hasNext: boolean
}

export interface CoverCandidate {
  url: string
  thumbnailUrl: string
  source: CoverSource
  attribution: string | null
  attributionUrl: string | null
  license: string | null
  downloadLocation: string | null
}

export interface SourceRef {
  url: string
  title: string | null
}

export interface CardDraft {
  mode: 'AI' | 'BASIC'
  card: CardInput
  fieldSources: Record<string, FactSource>
  suggestions: Record<string, string>
  confidence: {
    overall: ConfidenceLevel
    identification: ConfidenceLevel
    dates: ConfidenceLevel
    price: ConfidenceLevel
    location: ConfidenceLevel
  }
  unverifiedFields: string[]
  missingFields: string[]
  sources: SourceRef[]
  coverCandidates: CoverCandidate[]
  possibleDuplicates: { id: string; title: string }[]
  warnings: string[]
  originalInput: string
  enrichment: Record<string, unknown>
}

export interface DraftRequest {
  query?: string
  url?: string
  notes?: string
  seasons?: Season[]
  expiresOn?: string | null
}

export interface EnrichmentStatus {
  aiAvailable: boolean
  provider: string
  model: string | null
  imageProviders: string[]
  message: string
}

export interface Problem {
  type?: string
  title?: string
  status: number
  code: string
  detail?: string
  errors?: { field: string; message: string }[]
  existingCardId?: string
}
