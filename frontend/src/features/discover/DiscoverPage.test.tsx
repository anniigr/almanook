import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { baseHandlers, card, page } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

const idea = () =>
  card({
    id: 'idea',
    title: 'Vienna Christmas market',
    collection: null,
    discovery: { status: 'PENDING', createdAt: '2026-10-25T10:00:00Z', dismissedAt: null, savedAt: null },
  })

describe('Discover inbox', () => {
  it('offers clearly labelled Save and Dismiss actions on each idea', async () => {
    const actions: string[] = []
    server.use(
      http.post('/api/v1/discover/:id/:action', ({ params }) => {
        actions.push(`${params.action} ${params.id}`)
        return params.action === 'save'
          ? HttpResponse.json(card({ id: 'idea' }))
          : new HttpResponse(null, { status: 204 })
      }),
      ...baseHandlers({ discover: [idea()] }),
    )
    const { user } = renderApp('/discover')
    const tile = await screen.findByRole('article', { name: 'Vienna Christmas market' })
    await user.click(within(tile).getByRole('button', { name: 'Save Vienna Christmas market to collection' }))
    await waitFor(() => expect(actions).toEqual(['save idea']))
    expect(await screen.findByText('Saved to your collection')).toBeInTheDocument()

    await user.click(within(tile).getByRole('button', { name: 'Dismiss Vienna Christmas market' }))
    expect(await screen.findByText('Idea dismissed')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Undo' }))
    await waitFor(() => expect(actions).toEqual(['save idea', 'dismiss idea', 'restore idea']))
  })

  it('lists dismissed ideas separately with a restore action', async () => {
    const restored: string[] = []
    server.use(
      http.get('/api/v1/discover', ({ request }) =>
        HttpResponse.json(
          page(
            new URL(request.url).searchParams.get('status') === 'DISMISSED'
              ? [{ ...idea(), title: 'Old idea' }]
              : [],
          ),
        ),
      ),
      http.post('/api/v1/discover/:id/restore', ({ params }) => {
        restored.push(String(params.id))
        return new HttpResponse(null, { status: 204 })
      }),
      ...baseHandlers(),
    )
    const { user } = renderApp('/discover')
    await user.click(await screen.findByRole('tab', { name: 'Dismissed' }))
    const tile = await screen.findByRole('article', { name: 'Old idea' })
    await user.click(within(tile).getByRole('button', { name: /Restore/ }))
    await waitFor(() => expect(restored).toEqual(['idea']))
  })
})
