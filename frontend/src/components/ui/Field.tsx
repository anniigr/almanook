import { clsx } from 'clsx'
import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'

const CONTROL =
  'w-full rounded-lg border border-line bg-surface-2 px-3 text-sm text-text placeholder:text-faint ' +
  'transition-colors focus:border-accent focus:outline-none focus-visible:outline-none focus:ring-2 focus:ring-accent/30 ' +
  'aria-[invalid=true]:border-danger'

interface FieldShellProps {
  label: string
  hint?: ReactNode
  error?: string
  badge?: ReactNode
  className?: string
  children: (ids: { id: string; describedBy: string | undefined; invalid: boolean }) => ReactNode
}

/** Label + control + hint/error, wired with ids for assistive technology. */
export function FieldShell({ label, hint, error, badge, className, children }: FieldShellProps) {
  const id = useId()
  const hintId = hint ? id + '-hint' : undefined
  const errorId = error ? id + '-error' : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  return (
    <div className={clsx('flex flex-col gap-1.5', className)}>
      <div className="flex items-center gap-2">
        <label htmlFor={id} className="text-xs font-medium uppercase tracking-wide text-muted">
          {label}
        </label>
        {badge}
      </div>
      {children({ id, describedBy, invalid: !!error })}
      {hint && !error && (
        <p id={hintId} className="text-xs text-faint">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  hint?: ReactNode
  error?: string
  badge?: ReactNode
  wrapperClassName?: string
}

export const TextField = forwardRef<HTMLInputElement, InputProps>(function TextField(
  { label, hint, error, badge, wrapperClassName, className, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error} badge={badge} className={wrapperClassName}>
      {({ id, describedBy, invalid }) => (
        <input
          ref={ref}
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          className={clsx(CONTROL, 'h-10', className)}
          {...rest}
        />
      )}
    </FieldShell>
  )
})

type TextAreaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string
  hint?: ReactNode
  error?: string
  badge?: ReactNode
  wrapperClassName?: string
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextAreaField(
  { label, hint, error, badge, wrapperClassName, className, rows = 3, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error} badge={badge} className={wrapperClassName}>
      {({ id, describedBy, invalid }) => (
        <textarea
          ref={ref}
          id={id}
          rows={rows}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          className={clsx(CONTROL, 'py-2 leading-relaxed', className)}
          {...rest}
        />
      )}
    </FieldShell>
  )
})

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string
  hint?: ReactNode
  error?: string
  badge?: ReactNode
  wrapperClassName?: string
  options: { value: string; label: string }[]
}

export function SelectField({
  label,
  hint,
  error,
  badge,
  wrapperClassName,
  options,
  className,
  ...rest
}: SelectProps) {
  return (
    <FieldShell label={label} hint={hint} error={error} badge={badge} className={wrapperClassName}>
      {({ id, describedBy, invalid }) => (
        <select
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          className={clsx(CONTROL, 'h-10', className)}
          {...rest}
        >
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      )}
    </FieldShell>
  )
}
