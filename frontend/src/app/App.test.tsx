import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { baseHandlers } from '../test/fixtures'
import { renderApp } from '../test/render'
import { server } from '../test/server'

describe('navigation and auth', () => {
  it('redirects to the sign-in page without a session', async () => {
    server.use(
      http.get('/api/v1/me', () =>
        HttpResponse.json({ status: 401, code: 'unauthenticated' }, { status: 401 }),
      ),
    )
    renderApp('/collection')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows the four sections in the sidebar and switches pages', async () => {
    server.use(...baseHandlers())
    const { user } = renderApp('/collection')
    const nav = (await screen.findAllByRole('navigation', { name: 'Main' }))[0]!
    for (const label of ['My Collection', 'Discover', 'Expired', 'Settings']) {
      expect(within(nav).getByRole('link', { name: label })).toBeInTheDocument()
    }
    expect(screen.getByRole('heading', { level: 1, name: 'My Collection' })).toBeInTheDocument()

    await user.click(within(nav).getByRole('link', { name: 'Discover' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Discover' })).toBeInTheDocument()
    expect(await screen.findByText('Your inbox is clear')).toBeInTheDocument()

    await user.click(within(nav).getByRole('link', { name: 'Expired' }))
    expect(await screen.findByText('No expired events')).toBeInTheDocument()
  })

  it('signs in and lands on the collection', async () => {
    let signedIn = false
    server.use(
      http.get('/api/v1/me', () =>
        signedIn
          ? HttpResponse.json({
              id: 'u1',
              email: 'ada@example.com',
              displayName: 'Ada',
              preferences: { theme: 'SYSTEM', language: 'en', timezone: 'Europe/Warsaw' },
            })
          : HttpResponse.json({ status: 401, code: 'unauthenticated' }, { status: 401 }),
      ),
      http.post('/api/v1/auth/login', async ({ request }) => {
        const body = (await request.json()) as { email: string; password: string }
        if (body.password !== 'correct-horse') {
          return HttpResponse.json(
            { status: 401, code: 'bad_credentials', detail: 'Email or password is incorrect' },
            { status: 401 },
          )
        }
        signedIn = true
        return HttpResponse.json({
          id: 'u1',
          email: body.email,
          displayName: 'Ada',
          preferences: { theme: 'SYSTEM', language: 'en', timezone: 'Europe/Warsaw' },
        })
      }),
      ...baseHandlers(),
    )
    const { user } = renderApp('/login')
    await user.type(await screen.findByLabelText('Email'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect')

    await user.clear(screen.getByLabelText('Password'))
    await user.type(screen.getByLabelText('Password'), 'correct-horse')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'My Collection' })).toBeInTheDocument()
  })
})
