import { clsx } from 'clsx'
import type { ReactNode } from 'react'
import { BadgeCheck, PenLine, Sparkles } from 'lucide-react'
import type { FactSource, Season } from '../../api/types'
import { SEASON_LABEL } from '../../lib/format'

export function Chip({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-full bg-surface-2 px-2 py-0.5 text-[11px] text-muted',
        className,
      )}
    >
      {children}
    </span>
  )
}

export function SeasonChips({ seasons }: { seasons: Season[] }) {
  if (seasons.length === 0) return null
  return (
    <ul className="flex flex-wrap gap-1" aria-label="Seasons">
      {seasons.map((s) => (
        <li key={s}>
          <Chip>{SEASON_LABEL[s]}</Chip>
        </li>
      ))}
    </ul>
  )
}

const PROVENANCE: Record<FactSource, { label: string; title: string; icon: typeof BadgeCheck; cls: string }> =
  {
    SOURCE: {
      label: 'Verified',
      title: 'Taken from a retrieved source page',
      icon: BadgeCheck,
      cls: 'text-success',
    },
    AI: {
      label: 'AI suggestion',
      title: 'Suggested by AI, not verified',
      icon: Sparkles,
      cls: 'text-warning',
    },
    USER: { label: 'You', title: 'Entered by you', icon: PenLine, cls: 'text-faint' },
  }

export function ProvenanceBadge({
  source,
  unverified,
}: {
  source?: FactSource | null
  unverified?: boolean
}) {
  if (unverified) {
    return (
      <span
        className="inline-flex items-center gap-1 text-[11px] text-warning"
        title="Seen but not verified; left empty"
      >
        <Sparkles className="size-3" aria-hidden /> Unverified
      </span>
    )
  }
  if (!source) return null
  const p = PROVENANCE[source]
  const Icon = p.icon
  return (
    <span className={clsx('inline-flex items-center gap-1 text-[11px]', p.cls)} title={p.title}>
      <Icon className="size-3" aria-hidden />
      {p.label}
    </span>
  )
}
