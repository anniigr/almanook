import * as RadixDialog from '@radix-ui/react-dialog'
import { clsx } from 'clsx'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'

export interface DialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  /** Visually hide the title (it is still announced to screen readers). */
  hideTitle?: boolean
  size?: 'sm' | 'md' | 'lg'
  children: ReactNode
  footer?: ReactNode
}

const WIDTHS = { sm: 'max-w-md', md: 'max-w-2xl', lg: 'max-w-4xl' }

/** Accessible modal: focus trap, Escape to close, labelled by its title (Radix Dialog). */
export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  hideTitle,
  size = 'md',
  children,
  footer,
}: DialogProps) {
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-40 bg-overlay backdrop-blur-[2px] animate-fade-in" />
        <RadixDialog.Content
          className={clsx(
            'fixed left-1/2 top-1/2 z-50 flex max-h-[min(92dvh,900px)] w-[calc(100vw-1.5rem)] -translate-x-1/2 -translate-y-1/2 flex-col',
            'rounded-2xl border border-line bg-surface shadow-card animate-fade-in',
            WIDTHS[size],
          )}
        >
          <div className={clsx('flex items-start gap-4 px-6 pt-5', hideTitle ? 'pb-0' : 'pb-3')}>
            <div className="min-w-0 flex-1">
              <RadixDialog.Title className={clsx('font-serif text-xl text-text', hideTitle && 'sr-only')}>
                {title}
              </RadixDialog.Title>
              {description ? (
                <RadixDialog.Description className="mt-1 text-sm text-muted">
                  {description}
                </RadixDialog.Description>
              ) : (
                <RadixDialog.Description className="sr-only">{title}</RadixDialog.Description>
              )}
            </div>
            <RadixDialog.Close
              className="-mr-2 inline-flex size-9 items-center justify-center rounded-lg text-muted hover:bg-surface-2 hover:text-text"
              aria-label="Close"
            >
              <X className="size-5" aria-hidden />
            </RadixDialog.Close>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-6">{children}</div>
          {footer && (
            <div className="flex flex-wrap justify-end gap-2 border-t border-line px-6 py-4">{footer}</div>
          )}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  )
}
