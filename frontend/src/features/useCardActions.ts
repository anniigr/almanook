import type { Card } from '../api/types'
import {
  useDiscoverAction,
  useRemoveFromCollection,
  useSaveToCollection,
  useSetExpiration,
  useUpdatePersonal,
} from '../api/hooks'
import { ApiError } from '../api/client'
import { useToast } from '../components/ui/Toaster'

function message(e: unknown) {
  return e instanceof ApiError ? e.message : 'Something went wrong'
}

/** Card actions with consistent notifications; destructive ones offer Undo instead of a confirmation. */
export function useCardActions() {
  const toast = useToast()
  const save = useSaveToCollection()
  const remove = useRemoveFromCollection()
  const personal = useUpdatePersonal()
  const discover = useDiscoverAction()
  const expiration = useSetExpiration()

  const fail = (title: string) => (e: unknown) => toast({ title, description: message(e), tone: 'error' })

  return {
    busy:
      save.isPending || remove.isPending || personal.isPending || discover.isPending || expiration.isPending,

    saveToCollection(card: Card) {
      save.mutate(card.id, {
        onSuccess: () =>
          toast({ title: 'Saved to your collection', description: card.title, tone: 'success' }),
        onError: fail('Could not save'),
      })
    },

    removeFromCollection(card: Card) {
      remove.mutate(card.id, {
        onSuccess: () =>
          toast({
            title: 'Removed from collection',
            description: card.title,
            action: { label: 'Undo', onClick: () => save.mutate(card.id) },
          }),
        onError: fail('Could not remove'),
      })
    },

    toggleVisited(card: Card) {
      const visited = !card.collection?.visitedAt
      const noun = card.itemType === 'FILM' ? 'watched' : 'visited'
      personal.mutate(
        { id: card.id, visited },
        {
          onSuccess: () =>
            toast({ title: visited ? `Marked as ${noun}` : `No longer ${noun}`, description: card.title }),
          onError: fail('Could not update'),
        },
      )
    },

    saveIdea(card: Card) {
      discover.mutate(
        { id: card.id, action: 'save' },
        {
          onSuccess: () =>
            toast({ title: 'Saved to your collection', description: card.title, tone: 'success' }),
          onError: fail('Could not save'),
        },
      )
    },

    dismissIdea(card: Card) {
      discover.mutate(
        { id: card.id, action: 'dismiss' },
        {
          onSuccess: () =>
            toast({
              title: 'Idea dismissed',
              description: card.title,
              action: { label: 'Undo', onClick: () => discover.mutate({ id: card.id, action: 'restore' }) },
            }),
          onError: fail('Could not dismiss'),
        },
      )
    },

    restoreIdea(card: Card) {
      discover.mutate(
        { id: card.id, action: 'restore' },
        {
          onSuccess: () => toast({ title: 'Back in Discover', description: card.title }),
          onError: fail('Could not restore'),
        },
      )
    },

    /** Restoring an expired card clears its expiry, so it returns to the active collection. */
    restoreExpired(card: Card) {
      const previous = card.expiresOn
      expiration.mutate(
        { id: card.id, expiresOn: null },
        {
          onSuccess: () =>
            toast({
              title:
                card.discovery?.status === 'PENDING' ? 'Restored to Discover' : 'Restored to your collection',
              description: 'The end date was cleared. Edit the card to set a new one.',
              action: {
                label: 'Undo',
                onClick: () => expiration.mutate({ id: card.id, expiresOn: previous }),
              },
            }),
          onError: fail('Could not restore'),
        },
      )
    },
  }
}
