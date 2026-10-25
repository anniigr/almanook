import { useEffect, useRef, type ReactNode } from 'react'
import { Button } from '../ui/Button'
import { CardTileSkeleton } from './CardTile'

/** 1 column below 360 px, 2 on phones and tablets, 3 on medium desktops, 4 on wide desktops. */
export const GRID = 'grid grid-cols-1 gap-5 min-[360px]:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4'

export function CardGrid({
  children,
  loading,
  hasNext,
  fetchingNext,
  onLoadMore,
  label,
}: {
  children: ReactNode
  loading?: boolean
  hasNext?: boolean
  fetchingNext?: boolean
  onLoadMore?: () => void
  label: string
}) {
  const sentinel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = sentinel.current
    if (!el || !hasNext || !onLoadMore) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) onLoadMore()
      },
      { rootMargin: '600px 0px' },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasNext, onLoadMore])

  if (loading) {
    return (
      <div className={GRID} aria-busy="true" aria-label={`Loading ${label}`}>
        {Array.from({ length: 8 }, (_, i) => (
          <CardTileSkeleton key={i} />
        ))}
      </div>
    )
  }
  return (
    <>
      <section className={GRID} aria-label={label}>
        {children}
        {fetchingNext && Array.from({ length: 4 }, (_, i) => <CardTileSkeleton key={'more' + i} />)}
      </section>
      {hasNext && (
        <div ref={sentinel} className="flex justify-center py-8">
          <Button variant="ghost" size="sm" onClick={onLoadMore} loading={fetchingNext}>
            Load more
          </Button>
        </div>
      )}
    </>
  )
}
