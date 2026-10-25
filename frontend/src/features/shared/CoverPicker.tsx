import { clsx } from 'clsx'
import { ImageOff, Link2, Search, Upload } from 'lucide-react'
import { useRef, useState } from 'react'
import {
  reportUnsplashSelection,
  useCheckImageUrl,
  useCoverCandidates,
  useUploadImage,
} from '../../api/hooks'
import { ApiError } from '../../api/client'
import type { CoverCandidate, CoverInput, ItemType } from '../../api/types'
import { CoverImage } from '../../components/cards/CoverImage'
import { Button } from '../../components/ui/Button'

/** Preview the cover, choose a suggestion, search licensed images, paste a URL or upload a file. */
export function CoverPicker({
  value,
  onChange,
  candidates: initialCandidates,
  itemType,
  searchHint,
}: {
  value: CoverInput | null
  onChange: (c: CoverInput | null) => void
  candidates: CoverCandidate[]
  itemType: ItemType
  searchHint: string
}) {
  const [candidates, setCandidates] = useState(initialCandidates)
  const [query, setQuery] = useState(searchHint)
  const [pasted, setPasted] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const search = useCoverCandidates()
  const check = useCheckImageUrl()
  const upload = useUploadImage()

  const previewUrl = value
    ? value.source === 'UPLOAD'
      ? `/api/v1/images/${value.uploadId}`
      : value.url
    : null

  const choose = (c: CoverCandidate) => {
    setError(null)
    onChange({
      source: c.source,
      url: c.url,
      attribution: c.attribution,
      attributionUrl: c.attributionUrl,
      license: c.license,
    })
    if (c.downloadLocation) void reportUnsplashSelection(c.downloadLocation)
  }

  const runSearch = () => {
    if (!query.trim()) return
    setError(null)
    search.mutate(query.trim(), {
      onSuccess: (found) => {
        setCandidates(found)
        if (found.length === 0) setError('No licensed images found for this search.')
      },
      onError: () => setError('Image search is unavailable right now.'),
    })
  }

  const usePasted = () => {
    const url = pasted.trim()
    if (!url) return
    setError(null)
    check.mutate(url, {
      onSuccess: ({ usable }) => {
        if (usable) {
          onChange({ source: 'USER_URL', url })
          setPasted('')
        } else setError('That address is not a reachable https image (JPEG, PNG, WebP or GIF).')
      },
      onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not check the image.'),
    })
  }

  const onFile = (file: File | undefined) => {
    if (!file) return
    setError(null)
    upload.mutate(file, {
      onSuccess: ({ id }) => onChange({ source: 'UPLOAD', uploadId: id }),
      onError: (e) => setError(e instanceof ApiError ? e.message : 'Upload failed.'),
    })
  }

  return (
    <div className="space-y-3">
      <div className="flex gap-4">
        <CoverImage
          url={previewUrl}
          alt="Selected cover"
          itemType={itemType}
          className="aspect-[4/3] w-48 shrink-0 rounded-xl"
          eager
        />
        <div className="min-w-0 flex-1 space-y-2 text-xs text-muted">
          {value ? (
            <>
              <p>
                {value.source === 'UPLOAD'
                  ? 'Your uploaded image'
                  : value.source === 'USER_URL'
                    ? 'Image from your link'
                    : value.source === 'PAGE'
                      ? 'Image from the source page'
                      : value.attribution}
              </p>
              {value.license && <p>Licence: {value.license}</p>}
              <Button size="sm" variant="ghost" onClick={() => onChange(null)}>
                <ImageOff className="size-3.5" aria-hidden /> Remove cover
              </Button>
            </>
          ) : (
            <p>No cover selected. A neutral placeholder will be shown.</p>
          )}
        </div>
      </div>

      {candidates.length > 0 && (
        <fieldset className="flex gap-2 overflow-x-auto pb-1">
          <legend className="sr-only">Suggested covers</legend>
          {candidates.map((c) => {
            const selected = value?.url === c.url
            return (
              <label key={c.url} className="shrink-0 cursor-pointer">
                <input
                  type="radio"
                  name="cover-candidate"
                  checked={selected}
                  onChange={() => choose(c)}
                  aria-label={`Use cover from ${c.source.toLowerCase()}${c.attribution ? ': ' + c.attribution : ''}`}
                  className="peer sr-only"
                />
                <span
                  className={clsx(
                    'relative block aspect-[4/3] w-28 overflow-hidden rounded-lg border-2 transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-accent',
                    selected ? 'border-accent' : 'border-transparent hover:border-line-strong',
                  )}
                >
                  <img
                    src={c.thumbnailUrl}
                    alt=""
                    loading="lazy"
                    referrerPolicy="no-referrer"
                    className="size-full object-cover"
                  />
                  <span className="absolute bottom-1 left-1 rounded bg-black/60 px-1 text-[10px] text-white">
                    {c.source === 'PAGE' ? 'Official' : c.source === 'WIKIMEDIA' ? 'Wikimedia' : 'Unsplash'}
                  </span>
                </span>
              </label>
            )
          })}
        </fieldset>
      )}

      <div className="grid gap-2 sm:grid-cols-2">
        <div className="flex gap-2">
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                runSearch()
              }
            }}
            aria-label="Search licensed images"
            placeholder="Search licensed images"
            className="h-9 min-w-0 flex-1 rounded-lg border border-line bg-surface-2 px-3 text-sm text-text focus:border-accent focus:outline-none"
          />
          <Button size="sm" onClick={runSearch} loading={search.isPending} aria-label="Search images">
            <Search className="size-3.5" aria-hidden />
          </Button>
        </div>
        <div className="flex gap-2">
          <input
            value={pasted}
            onChange={(e) => setPasted(e.target.value)}
            aria-label="Image URL"
            placeholder="https://… image URL"
            className="h-9 min-w-0 flex-1 rounded-lg border border-line bg-surface-2 px-3 text-sm text-text focus:border-accent focus:outline-none"
          />
          <Button size="sm" onClick={usePasted} loading={check.isPending} aria-label="Use image URL">
            <Link2 className="size-3.5" aria-hidden />
          </Button>
          <Button
            size="sm"
            onClick={() => fileInput.current?.click()}
            loading={upload.isPending}
            aria-label="Upload image"
          >
            <Upload className="size-3.5" aria-hidden />
          </Button>
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="hidden"
            data-testid="cover-file"
            onChange={(e) => onFile(e.target.files?.[0])}
          />
        </div>
      </div>
      {error && (
        <p role="alert" className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
