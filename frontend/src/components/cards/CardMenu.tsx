import * as Menu from '@radix-ui/react-dropdown-menu'
import { MoreHorizontal } from 'lucide-react'
import type { ReactNode } from 'react'

export interface MenuAction {
  label: string
  icon: ReactNode
  onSelect: () => void
  danger?: boolean
}

/** Restrained overflow menu for a card's secondary actions (keyboard accessible via Radix). */
export function CardMenu({ title, actions }: { title: string; actions: MenuAction[] }) {
  return (
    <Menu.Root modal={false}>
      <Menu.Trigger
        aria-label={`More actions for ${title}`}
        className="inline-flex size-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text data-[state=open]:bg-surface-2"
      >
        <MoreHorizontal className="size-4" aria-hidden />
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Content
          align="end"
          sideOffset={4}
          className="z-50 min-w-48 rounded-xl border border-line bg-surface p-1 shadow-card animate-fade-in"
        >
          {actions.map((a, i) => (
            <Menu.Item
              key={a.label}
              onSelect={a.onSelect}
              className={
                'flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm outline-none ' +
                (a.danger
                  ? 'text-danger data-[highlighted]:bg-danger/10'
                  : 'text-text data-[highlighted]:bg-surface-2') +
                (a.danger && i > 0 ? ' mt-1 border-t border-line pt-2' : '')
              }
            >
              <span className="text-muted" aria-hidden>
                {a.icon}
              </span>
              {a.label}
            </Menu.Item>
          ))}
        </Menu.Content>
      </Menu.Portal>
    </Menu.Root>
  )
}
