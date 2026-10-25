import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { baseHandlers, card } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

describe('Expired archive', () => {
  it('shows ended events with their end date and restores them by clearing the expiry, with undo', async () => {
    const patches: unknown[] = []
    server.use(
      http.patch('/api/v1/cards/:id/expiration', async ({ request }) => {
        patches.push(await request.json())
        return HttpResponse.json(card({ id: 'ex' }))
      }),
      ...baseHandlers({
        expired: [
          card({
            id: 'ex',
            title: 'Summer exhibition',
            itemType: 'EXHIBITION',
            venueName: 'Zachęta',
            eventEndDate: '2026-09-28',
            expiresOn: '2026-09-28',
            expired: true,
          }),
        ],
      }),
    )
    const { user } = renderApp('/expired')
    const tile = await screen.findByRole('article', { name: 'Summer exhibition' })
    expect(within(tile).getByText('Ended 28 Sept 2026')).toBeInTheDocument()
    expect(within(tile).getByText(/Zachęta/)).toBeInTheDocument()

    await user.click(within(tile).getByRole('button', { name: /Restore/ }))
    await waitFor(() => expect(patches).toEqual([{ expiresOn: null }]))
    await user.click(await screen.findByRole('button', { name: 'Undo' }))
    await waitFor(() => expect(patches).toEqual([{ expiresOn: null }, { expiresOn: '2026-09-28' }]))
  })
})
