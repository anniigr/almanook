import {
  CalendarDays,
  CheckCircle2,
  Clock,
  ExternalLink,
  MapPin,
  MinusCircle,
  Pencil,
  Plus,
  Ticket,
  Trash2,
  Undo2,
  Wallet,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useCard, useDeleteCard, useUpdatePersonal } from '../../api/hooks'
import type { Card, FactSource } from '../../api/types'
import { useCardDialogs } from '../../app/ui-state'
import { CoverImage } from '../../components/cards/CoverImage'
import { ProvenanceBadge, SeasonChips } from '../../components/ui/Badges'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { Dialog } from '../../components/ui/Dialog'
import { ErrorState } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toaster'
import { expiryLabel, formatDate, formatDateRange, formatPrice, hostOf, TYPE_LABEL } from '../../lib/format'
import { useCardActions } from '../useCardActions'

export function CardDetailDialog() {
  const { detailId, close } = useCardDialogs()
  if (!detailId) return null
  return <Detail id={detailId} onClose={close} />
}

function Detail({ id, onClose }: { id: string; onClose: () => void }) {
  const query = useCard(id)
  const card = query.data
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={card?.title ?? 'Card'}
      hideTitle={!!card}
      size="lg"
    >
      {query.isPending ? (
        <div className="space-y-3 pt-2" aria-busy>
          <div className="skeleton aspect-[16/7] w-full rounded-xl" />
          <div className="skeleton h-6 w-1/2 rounded" />
          <div className="skeleton h-4 w-full rounded" />
        </div>
      ) : query.isError || !card ? (
        <ErrorState message="This card could not be loaded. It may have been deleted." />
      ) : (
        <DetailBody card={card} onClose={onClose} />
      )}
    </Dialog>
  )
}

function Fact({
  icon,
  label,
  children,
  source,
}: {
  icon: ReactNode
  label: string
  children: ReactNode
  source?: FactSource | null
}) {
  return (
    <div className="flex gap-3">
      <span className="mt-0.5 text-faint" aria-hidden>
        {icon}
      </span>
      <div className="min-w-0">
        <dt className="flex items-center gap-2 text-xs uppercase tracking-wide text-faint">
          {label} <ProvenanceBadge source={source} />
        </dt>
        <dd className="text-sm text-text">{children}</dd>
      </div>
    </div>
  )
}

function DetailBody({ card, onClose }: { card: Card; onClose: () => void }) {
  const { openEdit } = useCardDialogs()
  const actions = useCardActions()
  const del = useDeleteCard()
  const personal = useUpdatePersonal()
  const toast = useToast()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [note, setNote] = useState(card.collection?.personalNote ?? '')
  const inCollection = card.collection?.status === 'ACTIVE'
  const pending = card.discovery?.status === 'PENDING'
  const enrichment = card.enrichment as {
    sources?: { url: string; title?: string | null }[]
    mode?: string
  } | null
  const fieldSources = (card.enrichment?.fieldSources ?? {}) as Record<string, FactSource>
  const expiry = expiryLabel(card)
  const where = [card.venueName, card.address, card.city, card.country].filter(Boolean).join(', ')

  return (
    <div className="space-y-6 pt-1">
      <div className="relative">
        <CoverImage
          url={card.cover?.url}
          alt={card.title}
          itemType={card.itemType}
          className="aspect-[16/7] w-full rounded-xl"
          eager
        />
        {card.cover?.attribution && (
          <p className="mt-1.5 text-[11px] text-faint">
            {card.cover.attributionUrl ? (
              <a
                href={card.cover.attributionUrl}
                target="_blank"
                rel="noreferrer noopener"
                className="hover:text-muted"
              >
                {card.cover.attribution}
              </a>
            ) : (
              card.cover.attribution
            )}
          </p>
        )}
      </div>

      <header className="space-y-2">
        <p className="text-xs uppercase tracking-wide text-accent">{TYPE_LABEL[card.itemType]}</p>
        {/* The dialog's accessible title carries the name; this is its visual counterpart. */}
        <p aria-hidden className="font-serif text-3xl leading-tight text-text">
          {card.title}
        </p>
        {card.description && <p className="max-w-3xl leading-relaxed text-muted">{card.description}</p>}
        <div className="flex flex-wrap items-center gap-2">
          <SeasonChips seasons={card.seasons} />
          {expiry && (
            <span className={card.expired ? 'text-xs text-danger' : 'text-xs text-accent'}>{expiry}</span>
          )}
          {card.collection?.visitedAt && (
            <span className="inline-flex items-center gap-1 text-xs text-success">
              <CheckCircle2 className="size-3.5" aria-hidden />
              {card.itemType === 'FILM' ? 'Watched' : 'Visited'}{' '}
              {formatDate(card.collection.visitedAt.slice(0, 10))}
            </span>
          )}
        </div>
      </header>

      <div className="flex flex-wrap gap-2">
        <Button onClick={() => openEdit(card.id)}>
          <Pencil className="size-4" aria-hidden /> Edit
        </Button>
        {pending && (
          <Button variant="primary" onClick={() => actions.saveIdea(card)}>
            <Plus className="size-4" aria-hidden /> Save to collection
          </Button>
        )}
        {!inCollection && !pending && (
          <Button variant="primary" onClick={() => actions.saveToCollection(card)}>
            <Plus className="size-4" aria-hidden /> Save to collection
          </Button>
        )}
        {inCollection && (
          <Button onClick={() => actions.toggleVisited(card)}>
            <CheckCircle2 className="size-4" aria-hidden />
            {card.collection?.visitedAt
              ? 'Unmark'
              : card.itemType === 'FILM'
                ? 'Mark as watched'
                : 'Mark as visited'}
          </Button>
        )}
        {card.expired && (
          <Button onClick={() => actions.restoreExpired(card)}>
            <Undo2 className="size-4" aria-hidden /> Restore
          </Button>
        )}
        {inCollection && (
          <Button variant="ghost" onClick={() => actions.removeFromCollection(card)}>
            <MinusCircle className="size-4" aria-hidden /> Remove from collection
          </Button>
        )}
      </div>

      <dl className="grid gap-4 sm:grid-cols-2">
        {where && (
          <Fact
            icon={<MapPin className="size-4" />}
            label="Where"
            source={fieldSources.address ?? fieldSources.venueName}
          >
            {where}
          </Fact>
        )}
        {(card.eventStartDate || card.eventEndDate) && (
          <Fact icon={<CalendarDays className="size-4" />} label="Dates" source={card.eventDatesSource}>
            {formatDateRange(card.eventStartDate, card.eventEndDate)}
          </Fact>
        )}
        {card.price && (
          <Fact icon={<Wallet className="size-4" />} label="Price" source={card.price.source}>
            {formatPrice(card.price)}
            {card.price.note && <span className="text-muted"> · {card.price.note}</span>}
          </Fact>
        )}
        {card.expiresOn && (
          <Fact icon={<Clock className="size-4" />} label="Expires after" source={card.expirationSource}>
            {formatDate(card.expiresOn)}
          </Fact>
        )}
        {card.openingHoursNote && (
          <Fact
            icon={<Clock className="size-4" />}
            label="Opening hours"
            source={fieldSources.openingHoursNote}
          >
            {card.openingHoursNote}
          </Fact>
        )}
        {(card.creator || card.releaseYear) && (
          <Fact icon={<Ticket className="size-4" />} label={card.itemType === 'FILM' ? 'Film' : 'By'}>
            {[card.creator, card.releaseYear].filter(Boolean).join(' · ')}
          </Fact>
        )}
        {card.sourceUrl && (
          <Fact icon={<ExternalLink className="size-4" />} label="Link">
            <a
              href={card.sourceUrl}
              target="_blank"
              rel="noreferrer noopener"
              className="text-accent hover:underline"
            >
              {hostOf(card.sourceUrl)}
            </a>
          </Fact>
        )}
      </dl>

      {inCollection && (
        <div className="space-y-2">
          <label htmlFor="personal-note" className="text-xs font-medium uppercase tracking-wide text-muted">
            Personal note
          </label>
          <div className="flex gap-2">
            <textarea
              id="personal-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={2}
              maxLength={1000}
              className="min-w-0 flex-1 rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
            />
            <Button
              size="sm"
              disabled={note === (card.collection?.personalNote ?? '')}
              loading={personal.isPending}
              onClick={() =>
                personal.mutate(
                  { id: card.id, personalNote: note.trim() || null },
                  { onSuccess: () => toast({ title: 'Note saved' }) },
                )
              }
            >
              Save note
            </Button>
          </div>
        </div>
      )}

      {(enrichment?.sources?.length || card.originalInput) && (
        <details className="rounded-xl border border-line p-4 text-sm">
          <summary className="cursor-pointer text-muted hover:text-text">Where this card came from</summary>
          {card.originalInput && (
            <p className="mt-3 whitespace-pre-line text-muted">
              <span className="text-faint">You entered: </span>
              {card.originalInput}
            </p>
          )}
          {enrichment?.sources && enrichment.sources.length > 0 && (
            <ul className="mt-3 space-y-1">
              {enrichment.sources.map((s) => (
                <li key={s.url}>
                  <a
                    href={s.url}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="text-accent hover:underline"
                  >
                    {s.title || hostOf(s.url)}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </details>
      )}

      <div className="border-t border-line pt-4">
        <Button
          variant="ghost"
          size="sm"
          className="text-danger hover:text-danger"
          onClick={() => setConfirmDelete(true)}
        >
          <Trash2 className="size-4" aria-hidden /> Delete permanently
        </Button>
      </div>
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete this card permanently?"
        description="The card, its notes and its visited status will be deleted. To keep it but hide it, remove it from your collection instead."
        confirmLabel="Delete"
        loading={del.isPending}
        onConfirm={() =>
          del.mutate(card.id, {
            onSuccess: () => {
              toast({ title: 'Card deleted', description: card.title })
              setConfirmDelete(false)
              onClose()
            },
          })
        }
      />
    </div>
  )
}
