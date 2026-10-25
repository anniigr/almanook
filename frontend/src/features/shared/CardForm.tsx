import { clsx } from 'clsx'
import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import type { CardInput, CoverCandidate, CoverInput, FactSource, ItemType, Season } from '../../api/types'
import { ITEM_TYPES, SEASONS, TEMPORARY_TYPES } from '../../api/types'
import { ProvenanceBadge } from '../../components/ui/Badges'
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field'
import { SEASON_LABEL, TYPE_LABEL } from '../../lib/format'
import { CoverPicker } from './CoverPicker'

type Text = string

interface FormState {
  title: Text
  description: Text
  itemType: ItemType
  venueName: Text
  address: Text
  city: Text
  country: Text
  creator: Text
  releaseYear: Text
  sourceUrl: Text
  priceMin: Text
  priceMax: Text
  currency: Text
  priceNote: Text
  eventStartDate: Text
  eventEndDate: Text
  expiresOn: Text
  openingHoursNote: Text
  seasons: Season[]
}

const s = (v: string | number | null | undefined) => (v === null || v === undefined ? '' : String(v))
const orNull = (v: string) => (v.trim() === '' ? null : v.trim())
const numOrNull = (v: string) => (v.trim() === '' ? null : Number(v.replace(',', '.')))

function toState(c: CardInput): FormState {
  return {
    title: s(c.title),
    description: s(c.description),
    itemType: c.itemType,
    venueName: s(c.venueName),
    address: s(c.address),
    city: s(c.city),
    country: s(c.country),
    creator: s(c.creator),
    releaseYear: s(c.releaseYear),
    sourceUrl: s(c.sourceUrl),
    priceMin: s(c.priceMin),
    priceMax: s(c.priceMax),
    currency: s(c.currency) || (c.priceMin !== null && c.priceMin !== undefined ? '' : 'PLN'),
    priceNote: s(c.priceNote),
    eventStartDate: s(c.eventStartDate),
    eventEndDate: s(c.eventEndDate),
    expiresOn: s(c.expiresOn),
    openingHoursNote: s(c.openingHoursNote),
    seasons: c.seasons ?? [],
  }
}

export interface CardFormProps {
  id: string
  initial: CardInput
  /** Provenance of each field in a generated draft or saved card. */
  fieldSources?: Record<string, FactSource | null | undefined>
  unverifiedFields?: string[]
  coverCandidates?: CoverCandidate[]
  errors?: Record<string, string>
  onSubmit: (card: CardInput) => void
  children?: ReactNode
}

/**
 * The card editor. Any value the user changes loses its "verified"/"AI" provenance and is saved as user
 * input, so generated content never silently survives an edit or overwrites one.
 */
export function CardForm({
  id,
  initial,
  fieldSources = {},
  unverifiedFields = [],
  coverCandidates = [],
  errors = {},
  onSubmit,
  children,
}: CardFormProps) {
  const original = useMemo(() => toState(initial), [initial])
  const [form, setForm] = useState<FormState>(original)
  const [cover, setCover] = useState<CoverInput | null>(initial.cover ?? null)
  const [localError, setLocalError] = useState<Record<string, string>>({})
  const temporary = TEMPORARY_TYPES.has(form.itemType)
  const isFilm = form.itemType === 'FILM'

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((f) => {
      const next = { ...f, [key]: value }
      // Keep the expiry following the end date while it still mirrors it (temporary events only).
      if (
        key === 'eventEndDate' &&
        TEMPORARY_TYPES.has(f.itemType) &&
        (f.expiresOn === '' || f.expiresOn === f.eventEndDate)
      ) {
        next.expiresOn = value as string
      }
      return next
    })

  const changed = (...keys: (keyof FormState)[]) =>
    keys.some((k) => JSON.stringify(form[k]) !== JSON.stringify(original[k]))

  const badge = (field: string, ...keys: (keyof FormState)[]) => (
    <ProvenanceBadge
      source={changed(...(keys.length ? keys : [field as keyof FormState])) ? 'USER' : fieldSources[field]}
      unverified={
        unverifiedFields.includes(field) && !changed(...(keys.length ? keys : [field as keyof FormState]))
      }
    />
  )

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!form.title.trim()) errs.title = 'Title is required'
    const priceMin = numOrNull(form.priceMin)
    const priceMax = numOrNull(form.priceMax)
    if (priceMin !== null && (Number.isNaN(priceMin) || priceMin < 0))
      errs.priceMin = 'Enter a price of 0 or more'
    if (priceMax !== null && (Number.isNaN(priceMax) || priceMin === null || priceMax < priceMin))
      errs.priceMax = 'Must be at least the minimum price'
    if (priceMin !== null && !/^[A-Z]{3}$/.test(form.currency.trim().toUpperCase()))
      errs.currency = 'Use a code such as PLN'
    if (form.eventStartDate && form.eventEndDate && form.eventEndDate < form.eventStartDate)
      errs.eventEndDate = 'End date is before the start date'
    const year = numOrNull(form.releaseYear)
    if (year !== null && (Number.isNaN(year) || year < 1870 || year > 2200)) errs.releaseYear = 'Enter a year'
    if (form.sourceUrl && !/^https?:\/\/\S+$/i.test(form.sourceUrl.trim()))
      errs.sourceUrl = 'Must start with http:// or https://'
    setLocalError(errs)
    if (Object.keys(errs).length > 0) return

    const keep = (field: 'price' | 'eventDates' | 'expiresOn', ...keys: (keyof FormState)[]) =>
      changed(...keys) ? 'USER' : ((fieldSources[field] as FactSource | undefined) ?? 'USER')

    onSubmit({
      title: form.title.trim(),
      description: orNull(form.description),
      itemType: form.itemType,
      venueName: orNull(form.venueName),
      address: orNull(form.address),
      city: orNull(form.city),
      country: orNull(form.country),
      latitude: changed('address') ? null : (initial.latitude ?? null),
      longitude: changed('address') ? null : (initial.longitude ?? null),
      creator: orNull(form.creator),
      releaseYear: year,
      sourceUrl: orNull(form.sourceUrl),
      priceMin,
      priceMax,
      currency: priceMin === null ? null : form.currency.trim().toUpperCase(),
      priceNote: orNull(form.priceNote),
      priceSource: priceMin === null ? null : keep('price', 'priceMin', 'priceMax', 'currency'),
      eventStartDate: orNull(form.eventStartDate),
      eventEndDate: orNull(form.eventEndDate),
      eventDatesSource:
        form.eventStartDate || form.eventEndDate
          ? keep('eventDates', 'eventStartDate', 'eventEndDate')
          : null,
      expiresOn: orNull(form.expiresOn),
      expirationSource: form.expiresOn ? keep('expiresOn', 'expiresOn') : null,
      openingHoursNote: orNull(form.openingHoursNote),
      seasons: form.seasons,
      cover,
      version: initial.version,
    })
  }

  const err = (k: string) => localError[k] ?? errors[k]

  return (
    <form id={id} onSubmit={submit} noValidate className="space-y-6">
      {children}
      <section className="grid gap-4 sm:grid-cols-3">
        <TextField
          wrapperClassName="sm:col-span-2"
          label="Title"
          value={form.title}
          onChange={(e) => set('title', e.target.value)}
          error={err('title')}
          badge={badge('title')}
          required
          maxLength={200}
        />
        <SelectField
          label="Type"
          value={form.itemType}
          onChange={(e) => set('itemType', e.target.value as ItemType)}
          options={ITEM_TYPES.map((t) => ({ value: t, label: TYPE_LABEL[t] }))}
          badge={badge('itemType')}
        />
        <TextAreaField
          wrapperClassName="sm:col-span-3"
          label="Description"
          hint="Two or three short sentences: what it is and why go."
          value={form.description}
          onChange={(e) => set('description', e.target.value)}
          maxLength={1200}
          badge={badge('description')}
        />
      </section>

      <section aria-labelledby={id + '-where'} className="space-y-3">
        <h3 id={id + '-where'} className="text-sm font-medium text-text">
          {isFilm ? 'Film' : 'Where'}
        </h3>
        <div className="grid gap-4 sm:grid-cols-2">
          {isFilm ? (
            <>
              <TextField
                label="Director / creator"
                value={form.creator}
                onChange={(e) => set('creator', e.target.value)}
                badge={badge('creator')}
                maxLength={200}
              />
              <TextField
                label="Release year"
                inputMode="numeric"
                value={form.releaseYear}
                onChange={(e) => set('releaseYear', e.target.value)}
                error={err('releaseYear')}
              />
            </>
          ) : (
            <>
              <TextField
                label="Venue"
                value={form.venueName}
                onChange={(e) => set('venueName', e.target.value)}
                badge={badge('venueName')}
                maxLength={200}
              />
              <TextField
                label="Address"
                value={form.address}
                onChange={(e) => set('address', e.target.value)}
                badge={badge('address')}
                maxLength={300}
              />
              <TextField
                label="City"
                value={form.city}
                onChange={(e) => set('city', e.target.value)}
                badge={badge('city')}
                maxLength={120}
              />
              <TextField
                label="Country"
                value={form.country}
                onChange={(e) => set('country', e.target.value)}
                badge={badge('country')}
                maxLength={80}
              />
              <TextField
                wrapperClassName="sm:col-span-2"
                label="Artist / performer"
                value={form.creator}
                onChange={(e) => set('creator', e.target.value)}
                hint="Optional"
                maxLength={200}
              />
            </>
          )}
          <TextField
            wrapperClassName="sm:col-span-2"
            label="Source link"
            type="url"
            placeholder="https://"
            value={form.sourceUrl}
            onChange={(e) => set('sourceUrl', e.target.value)}
            error={err('sourceUrl')}
          />
        </div>
      </section>

      <section aria-labelledby={id + '-when'} className="space-y-3">
        <h3 id={id + '-when'} className="text-sm font-medium text-text">
          When & price
        </h3>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField
            label="Starts"
            type="date"
            value={form.eventStartDate}
            onChange={(e) => set('eventStartDate', e.target.value)}
            badge={badge('eventDates', 'eventStartDate', 'eventEndDate')}
          />
          <TextField
            label="Ends"
            type="date"
            value={form.eventEndDate}
            onChange={(e) => set('eventEndDate', e.target.value)}
            error={err('eventEndDate')}
          />
          <TextField
            label="Expires after"
            type="date"
            value={form.expiresOn}
            onChange={(e) => set('expiresOn', e.target.value)}
            badge={badge('expiresOn')}
            hint={
              temporary
                ? 'Follows the end date; moves to Expired after this day.'
                : 'Leave empty for permanent places.'
            }
          />
          <TextField
            label="Price from"
            inputMode="decimal"
            value={form.priceMin}
            onChange={(e) => set('priceMin', e.target.value)}
            error={err('priceMin')}
            badge={badge('price', 'priceMin', 'priceMax', 'currency')}
          />
          <TextField
            label="Price to"
            inputMode="decimal"
            value={form.priceMax}
            onChange={(e) => set('priceMax', e.target.value)}
            error={err('priceMax')}
            hint="Optional, for ranges"
          />
          <TextField
            label="Currency"
            value={form.currency}
            onChange={(e) => set('currency', e.target.value.toUpperCase())}
            error={err('currency')}
            maxLength={3}
          />
          <TextField
            wrapperClassName="sm:col-span-3"
            label="Price note"
            value={form.priceNote}
            onChange={(e) => set('priceNote', e.target.value)}
            placeholder="e.g. Reduced 10 PLN, free on Thursdays"
            maxLength={200}
          />
          {!isFilm && (
            <TextField
              wrapperClassName="sm:col-span-3"
              label="Opening hours"
              value={form.openingHoursNote}
              onChange={(e) => set('openingHoursNote', e.target.value)}
              badge={badge('openingHoursNote')}
              maxLength={300}
            />
          )}
        </div>
      </section>

      <section aria-labelledby={id + '-seasons'} className="space-y-2">
        <div className="flex items-center gap-2">
          <h3 id={id + '-seasons'} className="text-sm font-medium text-text">
            Seasons
          </h3>
          {badge('seasons')}
        </div>
        <SeasonToggles value={form.seasons} onChange={(v) => set('seasons', v)} />
        <p className="text-xs text-faint">None selected means non-seasonal.</p>
      </section>

      <section aria-labelledby={id + '-cover'} className="space-y-2">
        <h3 id={id + '-cover'} className="text-sm font-medium text-text">
          Cover
        </h3>
        <CoverPicker
          value={cover}
          onChange={setCover}
          candidates={coverCandidates}
          itemType={form.itemType}
          searchHint={[form.title, form.city].filter(Boolean).join(' ')}
        />
      </section>
    </form>
  )
}

export function SeasonToggles({ value, onChange }: { value: Season[]; onChange: (v: Season[]) => void }) {
  return (
    <fieldset className="flex flex-wrap gap-2">
      <legend className="sr-only">Seasons</legend>
      {SEASONS.map((season) => {
        const on = value.includes(season)
        return (
          <button
            key={season}
            type="button"
            aria-pressed={on}
            onClick={() => onChange(on ? value.filter((v) => v !== season) : [...value, season])}
            className={clsx(
              'rounded-full border px-3 py-1 text-[13px] transition-colors',
              on
                ? 'border-accent bg-accent/15 text-text'
                : 'border-line text-muted hover:border-line-strong hover:text-text',
            )}
          >
            {SEASON_LABEL[season]}
          </button>
        )
      })}
    </fieldset>
  )
}
