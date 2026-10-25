import { clsx } from 'clsx'
import { CheckCircle2, Hourglass } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Card } from '../../api/types'
import { expiryLabel, metaLine, TYPE_LABEL } from '../../lib/format'
import { SeasonChips } from '../ui/Badges'
import { CoverImage } from './CoverImage'

export interface CardTileProps {
  card: Card
  onOpen: (card: Card) => void
  /** Secondary actions, rendered as an overflow menu at the top right of the body. */
  menu?: ReactNode
  /** Primary actions (e.g. Discover's Save / Dismiss), rendered on the right of the footer. */
  actions?: ReactNode
  dimmed?: boolean
}

/** Image-led card: cover, title, one practical meta line, short description, seasons and expiry. */
export function CardTile({ card, onOpen, menu, actions, dimmed }: CardTileProps) {
  const expiry = expiryLabel(card)
  const soon = expiry?.startsWith('Ends') || expiry === 'Last day today'
  const visited = !!card.collection?.visitedAt
  const meta = metaLine(card)
  return (
    <article
      className={clsx(
        'group flex flex-col overflow-hidden rounded-2xl border border-line bg-surface shadow-card transition-colors duration-150 hover:border-line-strong',
        dimmed && 'opacity-80',
      )}
      aria-labelledby={`card-title-${card.id}`}
    >
      <button
        type="button"
        onClick={() => onOpen(card)}
        className="relative block text-left focus-visible:outline-offset-[-2px]"
        aria-label={`Open ${card.title}`}
        tabIndex={-1}
      >
        <CoverImage url={card.cover?.url} alt="" itemType={card.itemType} className="aspect-[4/3] w-full" />
        <span className="absolute left-3 top-3 rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-medium text-white backdrop-blur-sm">
          {TYPE_LABEL[card.itemType]}
        </span>
        {expiry && (
          <span
            className={clsx(
              'absolute bottom-3 left-3 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium backdrop-blur-sm',
              card.expired
                ? 'bg-black/60 text-white'
                : soon
                  ? 'bg-accent text-on-accent'
                  : 'bg-black/55 text-white',
            )}
          >
            <Hourglass className="size-3" aria-hidden />
            {expiry}
          </span>
        )}
      </button>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-start gap-2">
          <h3
            id={`card-title-${card.id}`}
            className="min-w-0 flex-1 font-serif text-[17px] leading-snug text-text"
          >
            <button
              type="button"
              onClick={() => onOpen(card)}
              className="text-left hover:text-accent-strong focus-visible:outline-offset-1"
            >
              {card.title}
            </button>
          </h3>
          {menu}
        </div>
        {meta && <p className="text-[13px] text-muted">{meta}</p>}
        {card.description && (
          <p className="line-clamp-3 text-[13px] leading-relaxed text-muted/90">{card.description}</p>
        )}
        <div className="mt-auto flex items-end gap-2 pt-1">
          <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1.5">
            <SeasonChips seasons={card.seasons} />
            {visited && (
              <span className="inline-flex items-center gap-1 text-[11px] text-success">
                <CheckCircle2 className="size-3.5" aria-hidden />
                {card.itemType === 'FILM' ? 'Watched' : 'Visited'}
              </span>
            )}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-1">{actions}</div>}
        </div>
      </div>
    </article>
  )
}

export function CardTileSkeleton() {
  return (
    <div className="overflow-hidden rounded-2xl border border-line bg-surface" aria-hidden>
      <div className="skeleton aspect-[4/3] w-full" />
      <div className="space-y-2 p-4">
        <div className="skeleton h-4 w-3/4 rounded" />
        <div className="skeleton h-3 w-1/2 rounded" />
        <div className="skeleton h-3 w-full rounded" />
      </div>
    </div>
  )
}
