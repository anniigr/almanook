import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import type { Destination } from '../api/types'

interface UiState {
  addOpen: boolean
  addDestination: Destination
  openAdd: (destination?: Destination) => void
  closeAdd: () => void
}

const UiContext = createContext<UiState | null>(null)

export function UiStateProvider({ children }: { children: ReactNode }) {
  const [addOpen, setAddOpen] = useState(false)
  const [addDestination, setAddDestination] = useState<Destination>('COLLECTION')
  const openAdd = useCallback((destination: Destination = 'COLLECTION') => {
    setAddDestination(destination)
    setAddOpen(true)
  }, [])
  const closeAdd = useCallback(() => setAddOpen(false), [])
  const value = useMemo(
    () => ({ addOpen, addDestination, openAdd, closeAdd }),
    [addOpen, addDestination, openAdd, closeAdd],
  )
  return <UiContext.Provider value={value}>{children}</UiContext.Provider>
}

export function useUi(): UiState {
  const ctx = useContext(UiContext)
  if (!ctx) throw new Error('useUi must be used inside UiStateProvider')
  return ctx
}

/** Card detail and edit dialogs are addressable through ?card=<id> and ?edit=<id>. */
export function useCardDialogs() {
  const [params, setParams] = useSearchParams()
  const set = useCallback(
    (key: 'card' | 'edit', id: string | null) =>
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          next.delete('card')
          next.delete('edit')
          if (id) next.set(key, id)
          return next
        },
        { replace: false },
      ),
    [setParams],
  )
  return {
    detailId: params.get('card'),
    editId: params.get('edit'),
    openDetail: (id: string) => set('card', id),
    openEdit: (id: string) => set('edit', id),
    close: () => set('card', null),
  }
}
