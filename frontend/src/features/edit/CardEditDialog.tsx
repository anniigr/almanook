import { useState } from 'react'
import { ApiError } from '../../api/client'
import { useCard, useUpdateCard } from '../../api/hooks'
import type { Card, CardInput, FactSource } from '../../api/types'
import { useCardDialogs } from '../../app/ui-state'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { useToast } from '../../components/ui/Toaster'
import { CardForm } from '../shared/CardForm'

export function CardEditDialog() {
  const { editId, openDetail, close } = useCardDialogs()
  if (!editId) return null
  return <Edit id={editId} onDone={() => openDetail(editId)} onClose={close} />
}

export function toInput(card: Card): CardInput {
  return {
    title: card.title,
    description: card.description,
    itemType: card.itemType,
    venueName: card.venueName,
    address: card.address,
    city: card.city,
    country: card.country,
    latitude: card.latitude,
    longitude: card.longitude,
    creator: card.creator,
    releaseYear: card.releaseYear,
    sourceUrl: card.sourceUrl,
    priceMin: card.price?.min ?? null,
    priceMax: card.price?.max ?? null,
    currency: card.price?.currency ?? null,
    priceNote: card.price?.note ?? null,
    priceSource: card.price?.source ?? null,
    eventStartDate: card.eventStartDate,
    eventEndDate: card.eventEndDate,
    eventDatesSource: card.eventDatesSource,
    expiresOn: card.expiresOn,
    expirationSource: card.expirationSource,
    openingHoursNote: card.openingHoursNote,
    seasons: card.seasons,
    cover: card.cover
      ? {
          source: card.cover.source,
          url: card.cover.source === 'UPLOAD' ? null : card.cover.url,
          uploadId: card.cover.uploadId,
          attribution: card.cover.attribution,
          attributionUrl: card.cover.attributionUrl,
          license: card.cover.license,
        }
      : null,
    version: card.version,
  }
}

function Edit({ id, onDone, onClose }: { id: string; onDone: () => void; onClose: () => void }) {
  const query = useCard(id)
  const update = useUpdateCard()
  const toast = useToast()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const card = query.data
  const sources: Record<string, FactSource | null> = card
    ? {
        ...((card.enrichment?.fieldSources ?? {}) as Record<string, FactSource>),
        price: card.price?.source ?? null,
        eventDates: card.eventDatesSource,
        expiresOn: card.expirationSource,
      }
    : {}

  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={card ? `Edit ${card.title}` : 'Edit card'}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            type="submit"
            form="edit-card-form"
            loading={update.isPending}
            disabled={!card}
          >
            Save changes
          </Button>
        </>
      }
    >
      {card ? (
        <CardForm
          key={card.version}
          id="edit-card-form"
          initial={toInput(card)}
          fieldSources={sources}
          errors={errors}
          onSubmit={(input) =>
            update.mutate(
              { id, card: input },
              {
                onSuccess: (saved) => {
                  toast({
                    title: 'Card updated',
                    description: saved.expired
                      ? 'Its end date has passed, so it is in Expired.'
                      : saved.title,
                    tone: 'success',
                  })
                  onDone()
                },
                onError: (e) => {
                  if (e instanceof ApiError) {
                    setErrors(e.fieldErrors())
                    toast({ title: 'Could not save', description: e.message, tone: 'error' })
                    if (e.code === 'stale_version') void query.refetch()
                  }
                },
              },
            )
          }
        />
      ) : (
        <div className="skeleton h-64 rounded-xl" aria-busy />
      )}
    </Dialog>
  )
}
