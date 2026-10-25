import { Archive, CheckCircle2, Pencil, Undo2 } from 'lucide-react'
import { useListing } from '../../api/hooks'
import { useCardDialogs } from '../../app/ui-state'
import { CardGrid } from '../../components/cards/CardGrid'
import { CardMenu } from '../../components/cards/CardMenu'
import { CardTile } from '../../components/cards/CardTile'
import { Button } from '../../components/ui/Button'
import { EmptyState, ErrorState } from '../../components/ui/States'
import { useListingFilters } from '../shared/FilterBar'
import { errorMessage, flatten } from '../shared/listing'
import { useCardActions } from '../useCardActions'

export function ExpiredPage() {
  const { q } = useListingFilters()
  const query = useListing('expired', { q })
  const { openDetail, openEdit } = useCardDialogs()
  const actions = useCardActions()
  const cards = flatten(query.data)

  return (
    <>
      <p className="mb-5 max-w-2xl text-sm text-muted">
        Events whose last day has passed in your timezone. Nothing here is deleted: restore a card, or edit
        its date if the event was extended.
      </p>
      {query.isError ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : !query.isPending && cards.length === 0 ? (
        <EmptyState icon={<Archive className="size-6" />} title={q ? 'Nothing matches' : 'No expired events'}>
          {!q && 'When an exhibition or festival in your collection ends, it moves here automatically.'}
        </EmptyState>
      ) : (
        <CardGrid
          label="Expired events"
          loading={query.isPending}
          hasNext={query.hasNextPage}
          fetchingNext={query.isFetchingNextPage}
          onLoadMore={() => void query.fetchNextPage()}
        >
          {cards.map((card) => (
            <CardTile
              key={card.id}
              card={card}
              onOpen={(c) => openDetail(c.id)}
              menu={
                <CardMenu
                  title={card.title}
                  actions={[
                    { label: 'Edit', icon: <Pencil className="size-4" />, onSelect: () => openEdit(card.id) },
                    ...(card.collection?.status === 'ACTIVE'
                      ? [
                          {
                            label: card.collection.visitedAt ? 'Mark as not visited' : 'Mark as visited',
                            icon: <CheckCircle2 className="size-4" />,
                            onSelect: () => actions.toggleVisited(card),
                          },
                        ]
                      : []),
                  ]}
                />
              }
              actions={
                <Button size="sm" onClick={() => actions.restoreExpired(card)} disabled={actions.busy}>
                  <Undo2 className="size-3.5" aria-hidden /> Restore
                </Button>
              }
            />
          ))}
        </CardGrid>
      )}
    </>
  )
}
