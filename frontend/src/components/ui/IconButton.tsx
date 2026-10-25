import { clsx } from 'clsx'
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { Tooltip } from './Tooltip'

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string
  icon: ReactNode
  tone?: 'default' | 'accent' | 'danger'
  withTooltip?: boolean
}

/** A square icon button that always has an accessible name, optionally shown as a tooltip. */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { label, icon, tone = 'default', withTooltip = true, className, type = 'button', ...rest },
  ref,
) {
  const button = (
    <button
      ref={ref}
      type={type}
      aria-label={label}
      className={clsx(
        'inline-flex size-9 shrink-0 items-center justify-center rounded-lg transition-colors duration-150',
        'disabled:cursor-not-allowed disabled:opacity-50',
        tone === 'default' && 'text-muted hover:bg-surface-2 hover:text-text',
        tone === 'accent' && 'bg-accent/15 text-accent hover:bg-accent/25',
        tone === 'danger' && 'text-muted hover:bg-danger/15 hover:text-danger',
        className,
      )}
      {...rest}
    >
      {icon}
    </button>
  )
  return withTooltip ? <Tooltip label={label}>{button}</Tooltip> : button
})
