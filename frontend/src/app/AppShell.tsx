import * as Menu from '@radix-ui/react-dropdown-menu'
import { clsx } from 'clsx'
import { Archive, Compass, LayoutGrid, LogOut, Moon, Plus, Search, Settings, Sun, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useLogout, useMe } from '../api/hooks'
import { Button } from '../components/ui/Button'
import { IconButton } from '../components/ui/IconButton'
import { AddCardDialog } from '../features/add/AddCardDialog'
import { CardDetailDialog } from '../features/detail/CardDetailDialog'
import { CardEditDialog } from '../features/edit/CardEditDialog'
import { useTheme } from '../theme/ThemeProvider'
import { useUi } from './ui-state'

const NAV = [
  { to: '/collection', label: 'My Collection', icon: LayoutGrid },
  { to: '/discover', label: 'Discover', icon: Compass },
  { to: '/expired', label: 'Expired', icon: Archive },
  { to: '/settings', label: 'Settings', icon: Settings },
] as const

const TITLES: Record<string, string> = {
  '/collection': 'My Collection',
  '/discover': 'Discover',
  '/expired': 'Expired',
  '/settings': 'Settings',
}

export function AppShell() {
  const location = useLocation()
  const title = TITLES[location.pathname] ?? 'Almanook'
  const searchable = location.pathname !== '/settings'

  useEffect(() => {
    document.title = `${title} · Almanook`
  }, [title])

  return (
    <div className="flex min-h-dvh">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[70] focus:rounded-lg focus:bg-accent focus:px-3 focus:py-2 focus:text-on-accent"
      >
        Skip to content
      </a>
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header title={title} searchable={searchable} />
        <MobileNav />
        <main id="main" className="flex-1 px-4 pb-16 pt-4 sm:px-6 lg:px-8" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
      <AddCardDialog />
      <CardDetailDialog />
      <CardEditDialog />
    </div>
  )
}

function Brand() {
  return (
    <div className="flex items-center gap-2.5 px-2">
      <img src="/favicon.svg" alt="" className="size-7" />
      <span className="font-serif text-lg tracking-tight text-text">Almanook</span>
    </div>
  )
}

function Sidebar() {
  return (
    <aside className="sticky top-0 hidden h-dvh w-56 shrink-0 flex-col gap-6 border-r border-line bg-surface/60 px-3 py-5 md:flex">
      <Brand />
      <nav aria-label="Main">
        <ul className="flex flex-col gap-0.5">
          {NAV.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                className={({ isActive }) =>
                  clsx(
                    'flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors',
                    isActive
                      ? 'bg-surface-2 font-medium text-text'
                      : 'text-muted hover:bg-surface-2/60 hover:text-text',
                  )
                }
              >
                <Icon className="size-4" aria-hidden />
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <p className="mt-auto px-3 text-[11px] leading-relaxed text-faint">Your almanac of places and plans.</p>
    </aside>
  )
}

function MobileNav() {
  return (
    <nav aria-label="Main" className="border-b border-line px-2 md:hidden">
      <ul className="flex gap-1 overflow-x-auto py-1">
        {NAV.map(({ to, label, icon: Icon }) => (
          <li key={to}>
            <NavLink
              to={to}
              className={({ isActive }) =>
                clsx(
                  'flex items-center gap-2 whitespace-nowrap rounded-lg px-3 py-2 text-sm',
                  isActive ? 'bg-surface-2 text-text' : 'text-muted',
                )
              }
            >
              <Icon className="size-4" aria-hidden />
              {label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

function Header({ title, searchable }: { title: string; searchable: boolean }) {
  const { openAdd } = useUi()
  const { resolved, toggle } = useTheme()
  const location = useLocation()
  return (
    <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-line bg-bg/85 px-4 py-3 backdrop-blur sm:px-6 lg:px-8">
      <div className="md:hidden">
        <img src="/favicon.svg" alt="Almanook" className="size-7" />
      </div>
      <h1 className="min-w-0 truncate font-serif text-xl text-text sm:text-2xl">{title}</h1>
      <div className="ml-auto flex items-center gap-2">
        {searchable && <SearchBox key={location.pathname} />}
        <Button
          variant="primary"
          onClick={() => openAdd(location.pathname === '/discover' ? 'DISCOVER' : 'COLLECTION')}
          aria-label="Add card"
        >
          <Plus className="size-4" aria-hidden />
          <span className="hidden sm:inline">Add Card</span>
        </Button>
        <IconButton
          label={resolved === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
          icon={resolved === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
          onClick={toggle}
        />
        <UserMenu />
      </div>
    </header>
  )
}

/** Debounced search bound to the current page's ?q= parameter. */
function SearchBox() {
  const [params, setParams] = useSearchParams()
  const [value, setValue] = useState(params.get('q') ?? '')

  useEffect(() => {
    const handle = setTimeout(() => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          if (value.trim()) next.set('q', value.trim())
          else next.delete('q')
          return next
        },
        { replace: true },
      )
    }, 250)
    return () => clearTimeout(handle)
  }, [value, setParams])

  return (
    <div className="relative hidden sm:block">
      <Search
        className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-faint"
        aria-hidden
      />
      <input
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Search title or description"
        aria-label="Search cards"
        className="h-10 w-56 rounded-lg border border-line bg-surface-2 pl-9 pr-8 text-sm text-text placeholder:text-faint focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30 lg:w-72 [&::-webkit-search-cancel-button]:hidden"
      />
      {value && (
        <button
          type="button"
          onClick={() => setValue('')}
          aria-label="Clear search"
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-faint hover:text-text"
        >
          <X className="size-3.5" aria-hidden />
        </button>
      )}
    </div>
  )
}

function UserMenu() {
  const { data: me } = useMe()
  const logout = useLogout()
  const navigate = useNavigate()
  if (!me) return null
  const initials = me.displayName
    .split(/\s+/)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
  return (
    <Menu.Root>
      <Menu.Trigger
        aria-label={`Account menu for ${me.displayName}`}
        className="flex size-9 items-center justify-center rounded-full bg-surface-3 text-xs font-semibold text-text hover:ring-2 hover:ring-line-strong"
      >
        {initials || '·'}
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Content
          align="end"
          sideOffset={6}
          className="z-50 min-w-56 rounded-xl border border-line bg-surface p-1 shadow-card"
        >
          <div className="px-3 py-2">
            <p className="text-sm text-text">{me.displayName}</p>
            <p className="text-xs text-muted">{me.email}</p>
          </div>
          <Menu.Separator className="my-1 h-px bg-line" />
          <Menu.Item
            onSelect={() => navigate('/settings')}
            className="flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm text-text outline-none data-[highlighted]:bg-surface-2"
          >
            <Settings className="size-4 text-muted" aria-hidden /> Settings
          </Menu.Item>
          <Menu.Item
            onSelect={() => logout.mutate(undefined, { onSettled: () => navigate('/login') })}
            className="flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm text-text outline-none data-[highlighted]:bg-surface-2"
          >
            <LogOut className="size-4 text-muted" aria-hidden /> Sign out
          </Menu.Item>
        </Menu.Content>
      </Menu.Portal>
    </Menu.Root>
  )
}
