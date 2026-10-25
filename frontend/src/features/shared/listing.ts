import { ApiError } from '../../api/client'
import type { Card } from '../../api/types'
import type { InfiniteData } from '@tanstack/react-query'
import type { PageResponse } from '../../api/types'

export function flatten(data: InfiniteData<PageResponse<Card>> | undefined): Card[] {
  return data?.pages.flatMap((p) => p.items) ?? []
}

export function totalOf(data: InfiniteData<PageResponse<Card>> | undefined): number | undefined {
  return data?.pages[0]?.totalItems
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Could not load cards.'
}
