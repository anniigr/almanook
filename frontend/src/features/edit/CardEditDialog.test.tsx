import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { CardInput } from '../../api/types'
import { baseHandlers, card } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

describe('editing a card', () => {
  it('edits dates, keeps untouched provenance and marks edited values as user input', async () => {
    const original = card({
      id: 'ex',
      version: 4,
      title: 'Winter exhibition',
      itemType: 'EXHIBITION',
      eventStartDate: '2026-10-01',
      eventEndDate: '2026-12-24',
      eventDatesSource: 'SOURCE',
      expiresOn: '2026-12-24',
      expirationSource: 'SOURCE',
      price: { min: 30, max: null, currency: 'PLN', note: null, source: 'SOURCE' },
    })
    let put: CardInput | undefined
    server.use(
      http.get('/api/v1/cards/ex', () => HttpResponse.json(original)),
      http.put('/api/v1/cards/ex', async ({ request }) => {
        put = (await request.json()) as CardInput
        return HttpResponse.json({ ...original, version: 5 })
      }),
      ...baseHandlers({ collection: [original] }),
    )
    const { user } = renderApp('/collection?edit=ex')
    const dialog = await screen.findByRole('dialog', { name: 'Edit Winter exhibition' })
    const ends = within(dialog).getByLabelText('Ends')
    await user.clear(ends)
    await user.type(ends, '2027-01-10')
    // the expiry followed the end date because it mirrored it
    expect(within(dialog).getByLabelText('Expires after')).toHaveValue('2027-01-10')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(put).toBeDefined())
    expect(put).toMatchObject({
      version: 4,
      eventEndDate: '2027-01-10',
      eventDatesSource: 'USER',
      expiresOn: '2027-01-10',
      expirationSource: 'USER',
      priceMin: 30,
      priceSource: 'SOURCE',
    })
  })
})
