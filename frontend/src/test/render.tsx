import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { createQueryClient, Providers, routes } from '../app/App'

/** Renders the real app (routes, providers, query client) at a path. */
export function renderApp(path = '/collection') {
  const client = createQueryClient()
  client.setDefaultOptions({ queries: { retry: false, staleTime: 0 }, mutations: { retry: false } })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const user = userEvent.setup()
  const utils = render(
    <Providers client={client}>
      <RouterProvider router={router} />
    </Providers>,
  )
  return { ...utils, user, router, client }
}
