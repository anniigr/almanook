import { clsx } from 'clsx'
import { Check, Compass, Eye, Pencil, Plus, Undo2, X } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { useListing } from '../../api/hooks'
import { useCardDialogs, useUi } from '../../app/ui-state'
import { CardGrid } from '../../components/cards/CardGrid'
import { CardMenu } from '../../components/cards/CardMenu'
import { CardTile } from '../../components/cards/CardTile'
import { Button } from '../../components/ui/Button'
import { IconButton } from '../../components/ui/IconButton'
import { EmptyState, ErrorState } from '../../components/ui/States'
import { FilterBar, useListingFilters } from '../shared/FilterBar'
import { errorMessage, flatten, totalOf } from '../shared/listing'
import { useCardActions } from '../useCardActions'

export function DiscoverPage() {
  const [params, setParams] = useSearchParams()
  const showDismissed = params.get('view') === 'dismissed'
  const filters = useListingFilters()
  const query = useListing(showDismissed ? 'dismissed' : 'discover', {
    q: filters.q,
    season: filters.season,
    sort: filters.sort,
  })
  const { openDetail, openEdit } = useCardDialogs()
  const { openAdd } = useUi()
  const actions = useCardActions()
  const cards = flatten(query.data)

  const setView = (dismissed: boolean) =>
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      if (dismissed) next.set('view', 'dismissed')
      else next.delete('view')
      return next
    })

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <p className="max-w-2xl text-sm text-muted">
          Ideas you have not decided on yet. Save the ones you want to keep; dismissed ideas can be restored.
        </p>
        <div
          role="tablist"
          aria-label="Inbox view"
          className="ml-auto flex rounded-lg border border-line bg-surface p-0.5"
        >
          {[
            { dismissed: false, label: 'Inbox' },
            { dismissed: true, label: 'Dismissed' },
          ].map((t) => (
            <button
              key={t.label}
              role="tab"
              type="button"
              aria-selected={showDismissed === t.dismissed}
              onClick={() => setView(t.dismissed)}
              className={clsx(
                'rounded-md px-3 py-1 text-[13px]',
                showDismissed === t.dismissed ? 'bg-surface-3 text-text' : 'text-muted hover:text-text',
              )}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>
      <FilterBar showSeasons={!showDismissed} total={totalOf(query.data)} />
      {query.isError ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : !query.isPending && cards.length === 0 ? (
        <EmptyState
          icon={<Compass className="size-6" />}
          title={
            showDismissed ? 'No dismissed ideas' : filters.active ? 'Nothing matches' : 'Your inbox is clear'
          }
          action={
            !showDismissed &&
            !filters.active && (
              <Button variant="primary" onClick={() => openAdd('DISCOVER')}>
                <Plus className="size-4" aria-hidden /> Add an idea
              </Button>
            )
          }
        >
          {!showDismissed &&
            !filters.active &&
            'Drop links and names here when you are not sure yet; decide later.'}
        </EmptyState>
      ) : (
        <CardGrid
          label={showDismissed ? 'Dismissed ideas' : 'Discover inbox'}
          loading={query.isPending}
          hasNext={query.hasNextPage}
          fetchingNext={query.isFetchingNextPage}
          onLoadMore={() => void query.fetchNextPage()}
        >
          {cards.map((card) => (
            <CardTile
              key={card.id}
              card={card}
              dimmed={showDismissed}
              onOpen={(c) => openDetail(c.id)}
              menu={
                <CardMenu
                  title={card.title}
                  actions={[
                    {
                      label: 'Open details',
                      icon: <Eye className="size-4" />,
                      onSelect: () => openDetail(card.id),
                    },
                    { label: 'Edit', icon: <Pencil className="size-4" />, onSelect: () => openEdit(card.id) },
                  ]}
                />
              }
              actions={
                showDismissed ? (
                  <Button size="sm" onClick={() => actions.restoreIdea(card)} disabled={actions.busy}>
                    <Undo2 className="size-3.5" aria-hidden /> Restore
                  </Button>
                ) : (
                  <>
                    <IconButton
                      label={`Dismiss ${card.title}`}
                      icon={<X className="size-4" />}
                      tone="danger"
                      onClick={() => actions.dismissIdea(card)}
                      disabled={actions.busy}
                    />
                    <IconButton
                      label={`Save ${card.title} to collection`}
                      icon={<Check className="size-4" />}
                      tone="accent"
                      onClick={() => actions.saveIdea(card)}
                      disabled={actions.busy}
                    />
                  </>
                )
              }
            />
          ))}
        </CardGrid>
      )}
    </>
  )
}
