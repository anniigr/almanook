import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { CreateCardBody } from '../../api/hooks'
import { aiOff, aiOn, baseHandlers, card, draft } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

function captureCreate() {
  const bodies: CreateCardBody[] = []
  server.use(
    http.post('/api/v1/cards', async ({ request }) => {
      const body = (await request.json()) as CreateCardBody
      bodies.push(body)
      return HttpResponse.json(card({ title: body.card.title }), { status: 201 })
    }),
  )
  return bodies
}

describe('Add Card', () => {
  it('creates a card manually without any AI provider', async () => {
    server.use(...baseHandlers({ status: aiOff }))
    const bodies = captureCreate()
    const { user } = renderApp('/collection')
    await user.click(await screen.findByRole('button', { name: 'Add card' }))
    const dialog = await screen.findByRole('dialog', { name: 'Add a card' })
    expect(within(dialog).getByText(/AI research is not configured/)).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('Place, event, link or note'), 'Łazienki Królewskie')
    expect(within(dialog).getByRole('button', { name: /Generate card/ })).toBeDisabled()
    await user.click(within(dialog).getByRole('button', { name: /Create manually/ }))

    const form = await screen.findByRole('dialog', { name: 'New card' })
    expect(within(form).getByLabelText('Title')).toHaveValue('Łazienki Królewskie')
    await user.type(within(form).getByLabelText('City'), 'Warszawa')
    await user.click(within(form).getByRole('button', { name: 'Spring' }))
    await user.click(within(form).getByRole('button', { name: 'Autumn' }))
    await user.click(within(form).getByRole('button', { name: 'Save card' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]!.destination).toBe('COLLECTION')
    expect(bodies[0]!.card).toMatchObject({
      title: 'Łazienki Królewskie',
      city: 'Warszawa',
      seasons: ['SPRING', 'AUTUMN'],
      expiresOn: null,
    })
    expect(await screen.findByText('Saved to your collection')).toBeInTheDocument()
  })

  it('generates a draft, shows provenance and unverified fields, and saves edits as user input', async () => {
    let draftRequest: unknown
    server.use(
      ...baseHandlers({ status: aiOn }),
      http.post('/api/v1/enrichment/drafts', async ({ request }) => {
        draftRequest = await request.json()
        return HttpResponse.json(draft())
      }),
    )
    const bodies = captureCreate()
    const { user } = renderApp('/collection')
    await user.click(await screen.findByRole('button', { name: 'Add card' }))
    const dialog = await screen.findByRole('dialog', { name: 'Add a card' })
    await user.type(
      within(dialog).getByLabelText('Place, event, link or note'),
      'Ogród Botaniczny w Powsinie',
    )
    await user.click(within(dialog).getByRole('button', { name: 'Spring' }))
    await user.click(within(dialog).getByRole('button', { name: /Generate card/ }))

    const review = await screen.findByRole('dialog', { name: 'Review the card' })
    expect(draftRequest).toMatchObject({ query: 'Ogród Botaniczny w Powsinie', seasons: ['SPRING'] })
    expect(within(review).getByText('Researched with AI')).toBeInTheDocument()
    expect(within(review).getByText(/Left empty \(unverified\): openingHoursNote/)).toBeInTheDocument()
    expect(within(review).getByText('Ogród Botaniczny PAN w Powsinie')).toBeInTheDocument()
    expect(within(review).getByText('Not verified: winter opening hours')).toBeInTheDocument()
    expect(within(review).getAllByText('Verified').length).toBeGreaterThan(0)
    expect(within(review).getAllByText('AI suggestion').length).toBeGreaterThan(0)

    const price = within(review).getByLabelText('Price from')
    await user.clear(price)
    await user.type(price, '18')
    await user.click(within(review).getByRole('button', { name: 'Save card' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]!.card.priceMin).toBe(18)
    expect(bodies[0]!.card.priceSource).toBe('USER')
    expect(bodies[0]!.originalInput).toBe('Ogród Botaniczny w Powsinie')
    expect(bodies[0]!.enrichment).toMatchObject({ mode: 'AI' })
  })

  it('keeps the input and offers manual creation when the AI provider fails', async () => {
    server.use(
      ...baseHandlers({ status: aiOn }),
      http.post('/api/v1/enrichment/drafts', () =>
        HttpResponse.json(
          {
            status: 502,
            code: 'ai_provider_error',
            detail: 'The AI provider is temporarily unavailable. Try again later.',
          },
          { status: 502 },
        ),
      ),
    )
    const { user } = renderApp('/collection')
    await user.click(await screen.findByRole('button', { name: 'Add card' }))
    const dialog = await screen.findByRole('dialog', { name: 'Add a card' })
    await user.type(within(dialog).getByLabelText('Place, event, link or note'), 'Opera Narodowa')
    await user.click(within(dialog).getByRole('button', { name: /Generate card/ }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('temporarily unavailable')
    expect(within(dialog).getByLabelText('Place, event, link or note')).toHaveValue('Opera Narodowa')
    await user.click(within(dialog).getByRole('button', { name: 'create the card manually' }))
    expect(await screen.findByRole('dialog', { name: 'New card' })).toBeInTheDocument()
  })

  it('points to the existing card when the link was already saved', async () => {
    server.use(
      ...baseHandlers({ status: aiOff }),
      http.post('/api/v1/cards', () =>
        HttpResponse.json(
          {
            status: 409,
            code: 'duplicate_card',
            detail: 'A card for this page already exists: MNW',
            existingCardId: 'c-existing',
          },
          { status: 409 },
        ),
      ),
    )
    const { user } = renderApp('/collection')
    await user.click(await screen.findByRole('button', { name: 'Add card' }))
    const dialog = await screen.findByRole('dialog', { name: 'Add a card' })
    await user.type(within(dialog).getByLabelText('Place, event, link or note'), 'MNW')
    await user.click(within(dialog).getByRole('button', { name: /Create manually/ }))
    const form = await screen.findByRole('dialog', { name: 'New card' })
    await user.click(within(form).getByRole('button', { name: 'Save card' }))
    expect(await within(form).findByRole('alert')).toHaveTextContent('Already saved')
    server.use(
      http.get('/api/v1/cards/c-existing', () => HttpResponse.json(card({ id: 'c-existing', title: 'MNW' }))),
    )
    await user.click(within(form).getByRole('button', { name: 'Open the existing card' }))
    expect(await screen.findByRole('dialog', { name: 'MNW' })).toBeInTheDocument()
  })
})
