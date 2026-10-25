import { AlertTriangle, ExternalLink, Info, PenLine, Sparkles } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { useCreateCard, useEnrichmentStatus, useGenerateDraft } from '../../api/hooks'
import type { CardDraft, CardInput, Destination, Season } from '../../api/types'
import { useCardDialogs, useUi } from '../../app/ui-state'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { TextAreaField, TextField } from '../../components/ui/Field'
import { useToast } from '../../components/ui/Toaster'
import { hostOf } from '../../lib/format'
import { CardForm, SeasonToggles } from '../shared/CardForm'

type Step = { kind: 'input' } | { kind: 'review'; draft: CardDraft | null; manual: CardInput | null }

const URL_RE = /^https?:\/\/\S+$/i

export function AddCardDialog() {
  const { addOpen, closeAdd, addDestination } = useUi()
  return addOpen ? <AddCardFlow destination={addDestination} onClose={closeAdd} /> : null
}

function AddCardFlow({
  destination: initialDestination,
  onClose,
}: {
  destination: Destination
  onClose: () => void
}) {
  const [step, setStep] = useState<Step>({ kind: 'input' })
  const [input, setInput] = useState('')
  const [notes, setNotes] = useState('')
  const [seasons, setSeasons] = useState<Season[]>([])
  const [expiresOn, setExpiresOn] = useState('')
  const [destination, setDestination] = useState<Destination>(initialDestination)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [duplicate, setDuplicate] = useState<{ id: string; message: string } | null>(null)
  const status = useEnrichmentStatus()
  const generate = useGenerateDraft()
  const create = useCreateCard()
  const toast = useToast()
  const { openDetail } = useCardDialogs()

  const trimmed = input.trim()
  const isUrl = URL_RE.test(trimmed)
  const aiAvailable = status.data?.aiAvailable ?? false
  const canGenerate = trimmed.length > 0 && (aiAvailable || isUrl)

  const onGenerate = (e?: FormEvent) => {
    e?.preventDefault()
    if (!canGenerate) return
    generate.mutate(
      {
        query: isUrl ? undefined : trimmed,
        url: isUrl ? trimmed : undefined,
        notes: notes.trim() || undefined,
        seasons: seasons.length ? seasons : undefined,
        expiresOn: expiresOn || null,
      },
      { onSuccess: (draft) => setStep({ kind: 'review', draft, manual: null }) },
    )
  }

  const onManual = () =>
    setStep({
      kind: 'review',
      draft: null,
      manual: {
        title: isUrl ? '' : trimmed,
        itemType: 'PLACE',
        sourceUrl: isUrl ? trimmed : null,
        seasons,
        expiresOn: expiresOn || null,
        expirationSource: expiresOn ? 'USER' : null,
      },
    })

  const save = (card: CardInput) => {
    const draft = step.kind === 'review' ? step.draft : null
    setErrors({})
    setDuplicate(null)
    create.mutate(
      {
        card,
        destination,
        originalInput:
          draft?.originalInput ??
          ([trimmed, notes.trim() && 'Notes: ' + notes.trim()].filter(Boolean).join('\n') || null),
        enrichment: draft?.enrichment ?? null,
      },
      {
        onSuccess: (saved) => {
          toast({
            title: destination === 'DISCOVER' ? 'Added to Discover' : 'Saved to your collection',
            description: saved.title,
            tone: 'success',
          })
          onClose()
        },
        onError: (e) => {
          if (e instanceof ApiError && e.code === 'duplicate_card' && e.problem.existingCardId) {
            // Shown inside the dialog: content outside a modal is inert, so a toast action would be unreachable.
            setDuplicate({ id: e.problem.existingCardId, message: e.message })
          } else if (e instanceof ApiError) {
            setErrors(e.fieldErrors())
            toast({ title: 'Could not save the card', description: e.message, tone: 'error' })
          }
        },
      },
    )
  }

  if (step.kind === 'review') {
    const draft = step.draft
    const initial = draft?.card ?? step.manual!
    return (
      <Dialog
        open
        onOpenChange={(o) => !o && onClose()}
        title={draft ? 'Review the card' : 'New card'}
        description={
          draft
            ? 'Check and edit anything before saving. Nothing is saved yet.'
            : 'Fill in what you know; everything except the title is optional.'
        }
        size="lg"
        footer={
          <>
            <Button variant="ghost" onClick={() => setStep({ kind: 'input' })}>
              Back
            </Button>
            <div className="mr-auto" />
            <label className="flex items-center gap-2 text-sm text-muted">
              Save to
              <select
                value={destination}
                onChange={(e) => setDestination(e.target.value as Destination)}
                className="h-9 rounded-lg border border-line bg-surface-2 px-2 text-sm text-text"
              >
                <option value="COLLECTION">My Collection</option>
                <option value="DISCOVER">Discover inbox</option>
              </select>
            </label>
            <Button variant="primary" type="submit" form="add-card-form" loading={create.isPending}>
              Save card
            </Button>
          </>
        }
      >
        <CardForm
          id="add-card-form"
          initial={initial}
          fieldSources={draft?.fieldSources}
          unverifiedFields={draft?.unverifiedFields}
          coverCandidates={draft?.coverCandidates}
          errors={errors}
          onSubmit={save}
        >
          {duplicate && (
            <div
              role="alert"
              className="flex flex-wrap items-center gap-3 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm"
            >
              <span className="flex-1 text-text">Already saved. {duplicate.message}</span>
              <Button
                size="sm"
                onClick={() => {
                  onClose()
                  openDetail(duplicate.id)
                }}
              >
                Open the existing card
              </Button>
            </div>
          )}
          {draft && <DraftSummary draft={draft} />}
        </CardForm>
      </Dialog>
    )
  }

  const generateError = generate.error instanceof ApiError ? generate.error : null
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Add a card"
      description="Type a place or event, paste a link, or both. Almanook researches it and prepares a card for you to review."
      footer={
        <>
          <Button variant="ghost" onClick={onManual}>
            <PenLine className="size-4" aria-hidden /> Create manually
          </Button>
          <Button
            variant="primary"
            type="submit"
            form="add-input-form"
            disabled={!canGenerate}
            loading={generate.isPending}
          >
            <Sparkles className="size-4" aria-hidden />
            {generate.isPending ? 'Researching…' : 'Generate card'}
          </Button>
        </>
      }
    >
      <form id="add-input-form" onSubmit={onGenerate} className="space-y-4" noValidate>
        <TextField
          label="Place, event, link or note"
          placeholder="Ogród Botaniczny w Powsinie  ·  https://…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          maxLength={2048}
        />
        <TextAreaField
          label="Notes (optional)"
          placeholder="e.g. go with Ola when the magnolias bloom"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={2}
          maxLength={2000}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <p className="text-xs font-medium uppercase tracking-wide text-muted">Seasons (optional)</p>
            <SeasonToggles value={seasons} onChange={setSeasons} />
          </div>
          <TextField
            label="Expires after (optional)"
            type="date"
            value={expiresOn}
            onChange={(e) => setExpiresOn(e.target.value)}
          />
        </div>
        {status.data && !aiAvailable && (
          <p className="flex gap-2 rounded-lg border border-line bg-surface-2 p-3 text-sm text-muted">
            <Info className="mt-0.5 size-4 shrink-0 text-accent" aria-hidden />
            <span>
              AI research is not configured on this server, so a name alone cannot be researched. Paste a link
              to import the page’s own details, or create the card manually.
            </span>
          </p>
        )}
        {generate.isPending && (
          <div aria-live="polite" className="space-y-2 rounded-lg border border-line p-3">
            <p className="text-sm text-muted">Looking for official sources, dates, prices and a cover…</p>
            <div className="skeleton h-2 w-full rounded" />
          </div>
        )}
        {generateError && (
          <div
            role="alert"
            className="flex gap-2 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-text"
          >
            <AlertTriangle className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden />
            <span>
              {generateError.message} Your input is kept, so you can try again or{' '}
              <button type="button" className="underline underline-offset-2" onClick={onManual}>
                create the card manually
              </button>
              .
            </span>
          </div>
        )}
      </form>
    </Dialog>
  )
}

function DraftSummary({ draft }: { draft: CardDraft }) {
  return (
    <div className="space-y-3 rounded-xl border border-line bg-surface-2/60 p-4 text-sm">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
        <span className="inline-flex items-center gap-1.5 text-text">
          <Sparkles className="size-4 text-accent" aria-hidden />
          {draft.mode === 'AI' ? 'Researched with AI' : 'Imported from the page (AI not configured)'}
        </span>
        <span className="text-muted">
          Confidence:{' '}
          <strong className="font-medium text-text">{draft.confidence.overall.toLowerCase()}</strong>
        </span>
        {draft.unverifiedFields.length > 0 && (
          <span className="text-warning">Left empty (unverified): {draft.unverifiedFields.join(', ')}</span>
        )}
      </div>
      {draft.suggestions.title && (
        <p className="text-muted">
          Official name: <span className="text-text">{draft.suggestions.title}</span>
        </p>
      )}
      {draft.warnings.length > 0 && (
        <ul className="list-disc space-y-0.5 pl-5 text-muted">
          {draft.warnings.map((w) => (
            <li key={w}>{w}</li>
          ))}
        </ul>
      )}
      {draft.possibleDuplicates.length > 0 && (
        <p className="text-warning">
          You may already have this: {draft.possibleDuplicates.map((d) => d.title).join(', ')}
        </p>
      )}
      {draft.sources.length > 0 && (
        <details>
          <summary className="cursor-pointer text-muted hover:text-text">
            Sources ({draft.sources.length})
          </summary>
          <ul className="mt-2 space-y-1">
            {draft.sources.map((s) => (
              <li key={s.url}>
                <a
                  href={s.url}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="inline-flex items-center gap-1 text-accent hover:underline"
                >
                  {s.title || hostOf(s.url)} <ExternalLink className="size-3" aria-hidden />
                </a>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  )
}
