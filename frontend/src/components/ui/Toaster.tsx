import * as RadixToast from '@radix-ui/react-toast'
import { clsx } from 'clsx'
import { X } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

export interface ToastOptions {
  title: string
  description?: string
  tone?: 'default' | 'success' | 'error'
  action?: { label: string; onClick: () => void }
  durationMs?: number
}

interface ToastItem extends ToastOptions {
  id: number
}

const ToastContext = createContext<((t: ToastOptions) => void) | null>(null)

/** Success/error notifications with an optional action such as "Undo". */
export function Toaster({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const push = useCallback((t: ToastOptions) => {
    setToasts((all) => [...all.slice(-3), { ...t, id: Date.now() + Math.random() }])
  }, [])
  const remove = (id: number) => setToasts((all) => all.filter((t) => t.id !== id))
  const value = useMemo(() => push, [push])

  return (
    <ToastContext.Provider value={value}>
      <RadixToast.Provider swipeDirection="right" duration={5000}>
        {children}
        {toasts.map((t) => (
          <RadixToast.Root
            key={t.id}
            duration={t.durationMs ?? (t.action ? 7000 : 4000)}
            onOpenChange={(open) => !open && remove(t.id)}
            className={clsx(
              'flex items-start gap-3 rounded-xl border bg-surface-2 p-4 shadow-card animate-fade-in',
              t.tone === 'error'
                ? 'border-danger/40'
                : t.tone === 'success'
                  ? 'border-success/40'
                  : 'border-line',
            )}
          >
            <div className="min-w-0 flex-1">
              <RadixToast.Title className="text-sm font-medium text-text">{t.title}</RadixToast.Title>
              {t.description && (
                <RadixToast.Description className="mt-0.5 text-sm text-muted">
                  {t.description}
                </RadixToast.Description>
              )}
            </div>
            {t.action && (
              <RadixToast.Action
                altText={t.action.label}
                onClick={t.action.onClick}
                className="rounded-md px-2 py-1 text-sm font-medium text-accent hover:bg-accent/15"
              >
                {t.action.label}
              </RadixToast.Action>
            )}
            <RadixToast.Close aria-label="Dismiss notification" className="text-faint hover:text-text">
              <X className="size-4" aria-hidden />
            </RadixToast.Close>
          </RadixToast.Root>
        ))}
        <RadixToast.Viewport className="fixed bottom-4 right-4 z-[60] flex w-[min(380px,calc(100vw-2rem))] flex-col gap-2 outline-none" />
      </RadixToast.Provider>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside Toaster')
  return ctx
}
