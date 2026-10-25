import { clsx } from 'clsx'
import {
  Building2,
  CalendarDays,
  Clapperboard,
  Drama,
  Flower2,
  Landmark,
  Music,
  PartyPopper,
  Sparkles,
  Store,
  Ticket,
  UtensilsCrossed,
  type LucideIcon,
} from 'lucide-react'
import { useState } from 'react'
import type { ItemType } from '../../api/types'

const TYPE_ICON: Record<ItemType, LucideIcon> = {
  PLACE: Flower2,
  MUSEUM: Landmark,
  EXHIBITION: Building2,
  PERFORMANCE: Drama,
  CONCERT: Music,
  FESTIVAL: PartyPopper,
  MARKET: Store,
  FILM: Clapperboard,
  RESTAURANT: UtensilsCrossed,
  ACTIVITY: Ticket,
  EVENT: CalendarDays,
  OTHER: Sparkles,
}

/**
 * Lazy-loaded cover with a skeleton while loading and a neutral, type-specific placeholder when there is no
 * image or it fails to load, so a broken image never breaks the card.
 */
export function CoverImage({
  url,
  alt,
  itemType,
  className,
  eager,
}: {
  url: string | null | undefined
  alt: string
  itemType: ItemType
  className?: string
  eager?: boolean
}) {
  const [state, setState] = useState<'loading' | 'loaded' | 'failed'>('loading')
  const [lastUrl, setLastUrl] = useState(url)
  if (url !== lastUrl) {
    setLastUrl(url)
    setState('loading')
  }
  const Icon = TYPE_ICON[itemType]
  const showPlaceholder = !url || state === 'failed'
  return (
    <div className={clsx('relative overflow-hidden bg-surface-2', className)}>
      {showPlaceholder ? (
        <div
          className="cover-placeholder absolute inset-0 flex items-center justify-center"
          data-testid="cover-placeholder"
        >
          <Icon className="size-10 text-faint" strokeWidth={1.25} aria-hidden />
        </div>
      ) : (
        <>
          {state === 'loading' && <div className="skeleton absolute inset-0" aria-hidden />}
          <img
            src={url}
            alt={alt}
            loading={eager ? 'eager' : 'lazy'}
            decoding="async"
            referrerPolicy="no-referrer"
            onLoad={() => setState('loaded')}
            onError={() => setState('failed')}
            className={clsx(
              'absolute inset-0 size-full object-cover transition-opacity duration-300',
              state === 'loaded' ? 'opacity-100' : 'opacity-0',
            )}
          />
        </>
      )}
    </div>
  )
}
