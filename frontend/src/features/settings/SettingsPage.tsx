import { clsx } from 'clsx'
import { Monitor, Moon, Sun } from 'lucide-react'
import { useMemo } from 'react'
import { useEnrichmentStatus, useMe, useUpdatePreferences } from '../../api/hooks'
import type { Theme } from '../../api/types'
import { SelectField } from '../../components/ui/Field'
import { useToast } from '../../components/ui/Toaster'
import { useTheme, type ThemePreference } from '../../theme/ThemeProvider'

const THEMES: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'system', label: 'System', icon: Monitor },
]

const toServerTheme = (p: ThemePreference): Theme =>
  p === 'dark' ? 'DARK' : p === 'light' ? 'LIGHT' : 'SYSTEM'

export function SettingsPage() {
  const { data: me } = useMe()
  const theme = useTheme()
  const update = useUpdatePreferences()
  const status = useEnrichmentStatus()
  const toast = useToast()
  const zones = useMemo(() => {
    const list =
      typeof Intl.supportedValuesOf === 'function'
        ? Intl.supportedValuesOf('timeZone')
        : ['Europe/Warsaw', 'UTC']
    return list.includes('Europe/Warsaw') ? list : ['Europe/Warsaw', ...list]
  }, [])
  if (!me) return null

  const save = (patch: Partial<typeof me.preferences>, message: string) =>
    update.mutate(
      { ...me.preferences, ...patch },
      {
        onSuccess: () => toast({ title: message, tone: 'success' }),
        onError: () => toast({ title: 'Could not save settings', tone: 'error' }),
      },
    )

  return (
    <div className="max-w-2xl space-y-10">
      <section aria-labelledby="appearance" className="space-y-3">
        <h2 id="appearance" className="font-serif text-lg text-text">
          Appearance
        </h2>
        <fieldset className="grid grid-cols-3 gap-2">
          <legend className="sr-only">Theme</legend>
          {THEMES.map(({ value, label, icon: Icon }) => (
            <label key={value} className="cursor-pointer">
              <input
                type="radio"
                name="theme"
                value={value}
                checked={theme.preference === value}
                onChange={() => {
                  theme.setPreference(value)
                  save({ theme: toServerTheme(value) }, 'Theme saved')
                }}
                className="peer sr-only"
              />
              <span
                className={clsx(
                  'flex flex-col items-center gap-2 rounded-xl border p-4 text-sm transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-accent',
                  theme.preference === value
                    ? 'border-accent bg-accent/10 text-text'
                    : 'border-line text-muted hover:text-text',
                )}
              >
                <Icon className="size-5" aria-hidden />
                {label}
              </span>
            </label>
          ))}
        </fieldset>
        <p className="text-xs text-faint">
          System follows your device. Your choice is remembered on this device and in your account.
        </p>
      </section>

      <section aria-labelledby="region" className="space-y-4">
        <h2 id="region" className="font-serif text-lg text-text">
          Time and language
        </h2>
        <SelectField
          label="Timezone"
          hint="Decides when an event counts as finished: it stays active through its last day in this timezone."
          value={me.preferences.timezone}
          onChange={(e) => save({ timezone: e.target.value }, 'Timezone saved')}
          options={zones.map((z) => ({ value: z, label: z.replace(/_/g, ' ') }))}
        />
        <SelectField
          label="Language"
          hint="The interface is in English; cards keep names in their local language."
          value={me.preferences.language}
          onChange={(e) => save({ language: e.target.value }, 'Language saved')}
          options={[
            { value: 'en', label: 'English' },
            { value: 'pl', label: 'Polski (card content preference)' },
          ]}
        />
      </section>

      <section aria-labelledby="ai" className="space-y-2">
        <h2 id="ai" className="font-serif text-lg text-text">
          Card generation
        </h2>
        <p className="text-sm text-muted">{status.data?.message ?? 'Checking…'}</p>
        {status.data?.aiAvailable && status.data.model && (
          <p className="text-xs text-faint">Model: {status.data.model}</p>
        )}
        {status.data && (
          <p className="text-xs text-faint">
            Cover sources: {status.data.imageProviders.join(', ').toLowerCase()}
          </p>
        )}
      </section>

      <section aria-labelledby="account" className="space-y-1">
        <h2 id="account" className="font-serif text-lg text-text">
          Account
        </h2>
        <p className="text-sm text-muted">
          {me.displayName} · {me.email}
        </p>
      </section>
    </div>
  )
}
