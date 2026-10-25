import { clsx } from 'clsx'
import { RotateCcw } from 'lucide-react'
import { useSearchParams } from 'react-router'
import type { SeasonFilter, SortOrder } from '../../api/types'
import { Button } from '../../components/ui/Button'

const SEASON_OPTIONS: { value: SeasonFilter; label: string }[] = [
  { value: 'ALL', label: 'All Seasons' },
  { value: 'SPRING', label: 'Spring' },
  { value: 'SUMMER', label: 'Summer' },
  { value: 'AUTUMN', label: 'Autumn' },
  { value: 'WINTER', label: 'Winter' },
  { value: 'NON_SEASONAL', label: 'Non-seasonal' },
]

const SORT_OPTIONS: { value: SortOrder; label: string }[] = [
  { value: 'RECENT', label: 'Recently added' },
  { value: 'ALPHABETICAL', label: 'Alphabetical' },
  { value: 'EVENT_DATE', label: 'Event date' },
]

const isSeason = (v: string | null): v is SeasonFilter => SEASON_OPTIONS.some((o) => o.value === v)
const isSort = (v: string | null): v is SortOrder => SORT_OPTIONS.some((o) => o.value === v)

/** Filter state lives in the URL so it survives reloads and can be shared. */
export function useListingFilters() {
  const [params, setParams] = useSearchParams()
  const season: SeasonFilter = isSeason(params.get('season')) ? (params.get('season') as SeasonFilter) : 'ALL'
  const sort: SortOrder = isSort(params.get('sort')) ? (params.get('sort') as SortOrder) : 'RECENT'
  const q = params.get('q') ?? ''
  const update = (key: string, value: string | null) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value) next.set(key, value)
        else next.delete(key)
        return next
      },
      { replace: true },
    )
  return {
    season,
    sort,
    q,
    active: season !== 'ALL' || sort !== 'RECENT' || q !== '',
    setSeason: (s: SeasonFilter) => update('season', s === 'ALL' ? null : s),
    setSort: (s: SortOrder) => update('sort', s === 'RECENT' ? null : s),
    clear: () =>
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          next.delete('season')
          next.delete('sort')
          next.delete('q')
          return next
        },
        { replace: true },
      ),
  }
}

export function FilterBar({ showSeasons = true, total }: { showSeasons?: boolean; total?: number }) {
  const f = useListingFilters()
  return (
    <div className="mb-5 flex flex-wrap items-center gap-3">
      {showSeasons && (
        <fieldset className="flex flex-wrap gap-1 rounded-xl border border-line bg-surface p-1">
          <legend className="sr-only">Season</legend>
          {SEASON_OPTIONS.map((o) => (
            <label key={o.value} className="cursor-pointer">
              <input
                type="radio"
                name="season-filter"
                value={o.value}
                checked={f.season === o.value}
                onChange={() => f.setSeason(o.value)}
                className="peer sr-only"
              />
              <span
                className={clsx(
                  'block rounded-lg px-3 py-1.5 text-[13px] transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-accent',
                  f.season === o.value ? 'bg-surface-3 font-medium text-text' : 'text-muted hover:text-text',
                )}
              >
                {o.label}
              </span>
            </label>
          ))}
        </fieldset>
      )}
      <label className="flex items-center gap-2 text-[13px] text-muted">
        <span>Sort</span>
        <select
          value={f.sort}
          onChange={(e) => f.setSort(e.target.value as SortOrder)}
          className="h-9 rounded-lg border border-line bg-surface px-2 text-[13px] text-text focus:border-accent focus:outline-none"
        >
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </label>
      {f.active && (
        <Button variant="ghost" size="sm" onClick={f.clear}>
          <RotateCcw className="size-3.5" aria-hidden /> Clear filters
        </Button>
      )}
      {total !== undefined && (
        <p className="ml-auto text-[13px] text-faint" aria-live="polite">
          {total === 1 ? '1 card' : `${total} cards`}
        </p>
      )}
    </div>
  )
}
