import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type ThemePreference = 'dark' | 'light' | 'system'
export type ResolvedTheme = 'dark' | 'light'

export const THEME_STORAGE_KEY = 'almanook.theme'

interface ThemeContextValue {
  preference: ThemePreference
  resolved: ResolvedTheme
  /** True once the user (or their account) chose explicitly; false while following the OS. */
  explicit: boolean
  setPreference: (p: ThemePreference) => void
  toggle: () => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function systemTheme(): ResolvedTheme {
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

function readStored(): ThemePreference | null {
  try {
    const v = localStorage.getItem(THEME_STORAGE_KEY)
    return v === 'dark' || v === 'light' || v === 'system' ? v : null
  } catch {
    return null
  }
}

/**
 * Dark by default; follows the OS colour scheme until the user picks a theme, then persists the choice in
 * localStorage (and, through ThemeServerSync, in the account preferences).
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(() => readStored() ?? 'system')
  const [explicit, setExplicit] = useState(() => readStored() !== null)
  const [system, setSystem] = useState<ResolvedTheme>(systemTheme)

  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: light)')
    if (!mq) return
    const onChange = () => setSystem(mq.matches ? 'light' : 'dark')
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  const resolved: ResolvedTheme = preference === 'system' ? system : preference

  useEffect(() => {
    document.documentElement.dataset.theme = resolved
  }, [resolved])

  const setPreference = useCallback((p: ThemePreference) => {
    setPreferenceState(p)
    setExplicit(true)
    try {
      localStorage.setItem(THEME_STORAGE_KEY, p)
    } catch {
      // storage unavailable (private mode): the choice lasts for this session only
    }
  }, [])

  const toggle = useCallback(
    () => setPreference(resolved === 'dark' ? 'light' : 'dark'),
    [resolved, setPreference],
  )

  const value = useMemo(
    () => ({ preference, resolved, explicit, setPreference, toggle }),
    [preference, resolved, explicit, setPreference, toggle],
  )
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider')
  return ctx
}
