import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import {
  createBrowserRouter,
  Navigate,
  Outlet,
  RouterProvider,
  useLocation,
  type RouteObject,
} from 'react-router'
import { ApiError } from '../api/client'
import { useMe } from '../api/hooks'
import { Toaster } from '../components/ui/Toaster'
import { TooltipProvider } from '../components/ui/Tooltip'
import { LoginPage } from '../features/auth/LoginPage'
import { CollectionPage } from '../features/collection/CollectionPage'
import { DiscoverPage } from '../features/discover/DiscoverPage'
import { ExpiredPage } from '../features/expired/ExpiredPage'
import { SettingsPage } from '../features/settings/SettingsPage'
import { ThemeProvider, useTheme } from '../theme/ThemeProvider'
import { AppShell } from './AppShell'
import { UiStateProvider } from './ui-state'

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: true,
        retry: (count, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
      },
      mutations: { retry: false },
    },
  })
}

/** Redirects to /login when there is no session. */
function RequireAuth() {
  const { data: me, isPending, isError } = useMe()
  const location = useLocation()
  if (isPending) {
    return <div className="flex min-h-dvh items-center justify-center text-sm text-muted">Loading…</div>
  }
  if (isError || !me) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return (
    <UiStateProvider>
      <ThemeServerSync />
      <Outlet />
    </UiStateProvider>
  )
}

/** Adopts the account's saved theme on a device that has no local choice yet. */
function ThemeServerSync() {
  const { data: me } = useMe()
  const { explicit, setPreference } = useTheme()
  useEffect(() => {
    if (!me || explicit) return
    if (me.preferences.theme === 'DARK') setPreference('dark')
    if (me.preferences.theme === 'LIGHT') setPreference('light')
  }, [me, explicit, setPreference])
  return null
}

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/collection" replace /> },
          { path: '/collection', element: <CollectionPage /> },
          { path: '/discover', element: <DiscoverPage /> },
          { path: '/expired', element: <ExpiredPage /> },
          { path: '/settings', element: <SettingsPage /> },
          { path: '*', element: <Navigate to="/collection" replace /> },
        ],
      },
    ],
  },
]

export function Providers({ children, client }: { children: ReactNode; client: QueryClient }) {
  return (
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <TooltipProvider>
          <Toaster>{children}</Toaster>
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

export function App() {
  const [client] = useState(createQueryClient)
  const [router] = useState(() => createBrowserRouter(routes))
  return (
    <Providers client={client}>
      <RouterProvider router={router} />
    </Providers>
  )
}
