import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError, ensureCsrf, toQuery } from './client'
import type {
  Card,
  CardDraft,
  CardInput,
  CoverCandidate,
  Destination,
  DraftRequest,
  EnrichmentStatus,
  Me,
  PageResponse,
  Preferences,
  SeasonFilter,
  SortOrder,
} from './types'

export type ListingKind = 'collection' | 'discover' | 'dismissed' | 'expired'

export interface ListingParams {
  q?: string
  season?: SeasonFilter
  sort?: SortOrder
}

const PAGE_SIZE = 24

export const keys = {
  me: ['me'] as const,
  cards: ['cards'] as const,
  listing: (kind: ListingKind, params: ListingParams) => ['cards', 'listing', kind, params] as const,
  card: (id: string) => ['cards', 'one', id] as const,
  enrichmentStatus: ['enrichment-status'] as const,
}

// --- identity ---

export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: async (): Promise<Me | null> => {
      try {
        return await api<Me>('GET', '/me')
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
    staleTime: 60_000,
    retry: false,
  })
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; password: string }) => api<Me>('POST', '/auth/login', body),
    onSuccess: async (me) => {
      await ensureCsrf(true)
      qc.setQueryData(keys.me, me)
    },
  })
}

export function useRegister() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; password: string; displayName: string }) =>
      api<Me>('POST', '/auth/register', body),
    onSuccess: async (me) => {
      await ensureCsrf(true)
      qc.setQueryData(keys.me, me)
    },
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('POST', '/auth/logout'),
    onSettled: async () => {
      await ensureCsrf(true)
      qc.clear()
      qc.setQueryData(keys.me, null)
    },
  })
}

export function useUpdatePreferences() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (prefs: Preferences) => api<Preferences>('PUT', '/me/preferences', prefs),
    onSuccess: (preferences) => {
      qc.setQueryData<Me | null>(keys.me, (me) => (me ? { ...me, preferences } : me))
      // "today" depends on the timezone, so expiry-based listings must be refetched
      void qc.invalidateQueries({ queryKey: keys.cards })
    },
  })
}

// --- listings ---

function listingPath(kind: ListingKind, params: ListingParams, page: number) {
  const common = { q: params.q, page, size: PAGE_SIZE }
  switch (kind) {
    case 'collection':
      return '/collection' + toQuery({ ...common, season: params.season, sort: params.sort })
    case 'discover':
      return '/discover' + toQuery({ ...common, season: params.season, sort: params.sort })
    case 'dismissed':
      return '/discover' + toQuery({ ...common, status: 'DISMISSED' })
    case 'expired':
      return '/expired' + toQuery(common)
  }
}

export function useListing(kind: ListingKind, params: ListingParams) {
  return useInfiniteQuery({
    queryKey: keys.listing(kind, params),
    queryFn: ({ pageParam }) => api<PageResponse<Card>>('GET', listingPath(kind, params, pageParam)),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  })
}

export function useCard(id: string | null) {
  return useQuery({
    queryKey: keys.card(id ?? ''),
    queryFn: () => api<Card>('GET', '/cards/' + id),
    enabled: !!id,
  })
}

// --- card mutations ---

function useInvalidateCards() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: keys.cards })
}

export interface CreateCardBody {
  card: CardInput
  destination: Destination
  originalInput?: string | null
  enrichment?: Record<string, unknown> | null
}

export function useCreateCard() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: (body: CreateCardBody) => api<Card>('POST', '/cards', body),
    onSuccess: invalidate,
  })
}

export function useUpdateCard() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: ({ id, card }: { id: string; card: CardInput }) => api<Card>('PUT', '/cards/' + id, card),
    onSuccess: invalidate,
  })
}

export function useSetExpiration() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: ({ id, expiresOn }: { id: string; expiresOn: string | null }) =>
      api<Card>('PATCH', `/cards/${id}/expiration`, { expiresOn }),
    onSuccess: invalidate,
  })
}

export function useDeleteCard() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: (id: string) => api<void>('DELETE', '/cards/' + id),
    onSuccess: invalidate,
  })
}

export function useSaveToCollection() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: (id: string) => api<Card>('PUT', '/collection/' + id),
    onSuccess: invalidate,
  })
}

export function useRemoveFromCollection() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: (id: string) => api<void>('DELETE', '/collection/' + id),
    onSuccess: invalidate,
  })
}

export function useUpdatePersonal() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: string; visited?: boolean; personalNote?: string | null }) =>
      api<Card>('PATCH', '/collection/' + id, body),
    onSuccess: invalidate,
  })
}

export function useDiscoverAction() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'save' | 'dismiss' | 'restore' }) =>
      api<Card | void>('POST', `/discover/${id}/${action}`),
    onSuccess: invalidate,
  })
}

// --- enrichment & images ---

export function useEnrichmentStatus() {
  return useQuery({
    queryKey: keys.enrichmentStatus,
    queryFn: () => api<EnrichmentStatus>('GET', '/enrichment/status'),
    staleTime: 5 * 60_000,
  })
}

export function useGenerateDraft() {
  return useMutation({ mutationFn: (req: DraftRequest) => api<CardDraft>('POST', '/enrichment/drafts', req) })
}

export function useCoverCandidates() {
  return useMutation({
    mutationFn: (query: string) => api<CoverCandidate[]>('POST', '/images/candidates', { query }),
  })
}

export function useUploadImage() {
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return api<{ id: string; url: string }>('POST', '/images', form)
    },
  })
}

export function useCheckImageUrl() {
  return useMutation({
    mutationFn: (url: string) => api<{ usable: boolean }>('POST', '/images/check-url', { url }),
  })
}

export function reportUnsplashSelection(downloadLocation: string) {
  return api<void>('POST', '/images/unsplash-selection', { downloadLocation }).catch(() => undefined)
}
