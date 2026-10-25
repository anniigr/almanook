import { CheckCircle2, Eye, LayoutGrid, MinusCircle, Pencil, Plus } from 'lucide-react'
import { useListing } from '../../api/hooks'
import { useCardDialogs, useUi } from '../../app/ui-state'
import { CardGrid } from '../../components/cards/CardGrid'
import { CardMenu } from '../../components/cards/CardMenu'
import { CardTile } from '../../components/cards/CardTile'
import { Button } from '../../components/ui/Button'
import { EmptyState, ErrorState } from '../../components/ui/States'
import { FilterBar, useListingFilters } from '../shared/FilterBar'
import { errorMessage, flatten, totalOf } from '../shared/listing'
import { useCardActions } from '../useCardActions'

export function CollectionPage() {
  const filters = useListingFilters()
  const query = useListing('collection', { q: filters.q, season: filters.season, sort: filters.sort })
  const { openDetail, openEdit } = useCardDialogs()
  const { openAdd } = useUi()
  const actions = useCardActions()
  const cards = flatten(query.data)

  return (
    <>
      <FilterBar total={totalOf(query.data)} />
      {query.isError ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : !query.isPending && cards.length === 0 ? (
        filters.active ? (
          <EmptyState icon={<LayoutGrid className="size-6" />} title="Nothing matches these filters">
            Try another season or search term, or clear the filters.
            <div className="mt-4">
              <Button size="sm" onClick={filters.clear}>
                Clear filters
              </Button>
            </div>
          </EmptyState>
        ) : (
          <EmptyState
            icon={<LayoutGrid className="size-6" />}
            title="Your collection is empty"
            action={
              <Button variant="primary" onClick={() => openAdd('COLLECTION')}>
                <Plus className="size-4" aria-hidden /> Add your first card
              </Button>
            }
          >
            Add a place, exhibition, concert or film you want to get to. Paste a name or a link and Almanook
            prepares the card.
          </EmptyState>
        )
      ) : (
        <CardGrid
          label="Collection"
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
                    {
                      label: 'Open details',
                      icon: <Eye className="size-4" />,
                      onSelect: () => openDetail(card.id),
                    },
                    { label: 'Edit', icon: <Pencil className="size-4" />, onSelect: () => openEdit(card.id) },
                    {
                      label: card.collection?.visitedAt
                        ? card.itemType === 'FILM'
                          ? 'Mark as not watched'
                          : 'Mark as not visited'
                        : card.itemType === 'FILM'
                          ? 'Mark as watched'
                          : 'Mark as visited',
                      icon: <CheckCircle2 className="size-4" />,
                      onSelect: () => actions.toggleVisited(card),
                    },
                    {
                      label: 'Remove from collection',
                      icon: <MinusCircle className="size-4" />,
                      onSelect: () => actions.removeFromCollection(card),
                      danger: true,
                    },
                  ]}
                />
              }
            />
          ))}
        </CardGrid>
      )}
    </>
  )
}
