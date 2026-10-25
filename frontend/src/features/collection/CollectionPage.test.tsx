import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { baseHandlers, card, page } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

describe('My Collection', () => {
  it('renders image-led cards with type-specific information', async () => {
    server.use(
      ...baseHandlers({
        collection: [
          card({
            title: 'Tamara Łempicka',
            itemType: 'EXHIBITION',
            venueName: 'Muzeum Narodowe',
            eventStartDate: '2026-10-03',
            eventEndDate: '2027-01-17',
            expiresOn: '2027-01-17',
            price: { min: 25, max: 35, currency: 'PLN', note: null, source: 'SOURCE' },
            seasons: ['AUTUMN', 'WINTER'],
          }),
          card({
            title: 'Perfect Days',
            itemType: 'FILM',
            creator: 'Wim Wenders',
            releaseYear: 2023,
            city: null,
          }),
        ],
      }),
    )
    renderApp('/collection')
    const exhibition = await screen.findByRole('article', { name: 'Tamara Łempicka' })
    expect(within(exhibition).getByText(/Muzeum Narodowe · 3 Oct 2026 – 17 Jan 2027/)).toBeInTheDocument()
    expect(within(exhibition).getByText(/PLN\s?25–35/)).toBeInTheDocument()
    expect(within(exhibition).getByText('Autumn')).toBeInTheDocument()
    expect(within(exhibition).getByText('Until 17 Jan 2027')).toBeInTheDocument()
    const film = screen.getByRole('article', { name: 'Perfect Days' })
    expect(within(film).getByText('Wim Wenders · 2023')).toBeInTheDocument()
    expect(within(film).queryByText(/Until|Ends/)).not.toBeInTheDocument()
    expect(screen.getByText('2 cards')).toBeInTheDocument()
  })

  it('sends the season filter and sort to the API and can clear them', async () => {
    const requests: URLSearchParams[] = []
    server.use(
      http.get('/api/v1/collection', ({ request }) => {
        const params = new URL(request.url).searchParams
        requests.push(params)
        return HttpResponse.json(
          page(
            params.get('season') === 'SPRING'
              ? [card({ title: 'Garden', seasons: ['SPRING'] })]
              : [card({ title: 'Museum' }), card({ title: 'Garden', seasons: ['SPRING'] })],
          ),
        )
      }),
      ...baseHandlers(),
    )
    const { user } = renderApp('/collection')
    expect(await screen.findByRole('article', { name: 'Museum' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'All Seasons' })).toBeChecked()

    await user.click(screen.getByRole('radio', { name: 'Spring' }))
    await waitFor(() => expect(screen.queryByRole('article', { name: 'Museum' })).not.toBeInTheDocument())
    expect(requests.at(-1)?.get('season')).toBe('SPRING')

    await user.selectOptions(screen.getByLabelText('Sort'), 'EVENT_DATE')
    await waitFor(() => expect(requests.at(-1)?.get('sort')).toBe('EVENT_DATE'))

    await user.click(screen.getByRole('button', { name: /Clear filters/ }))
    await waitFor(() => expect(requests.at(-1)?.get('season')).toBe('ALL'))
    expect(requests.at(-1)?.get('sort')).toBe('RECENT')
    expect(screen.getByRole('radio', { name: 'All Seasons' })).toBeChecked()
  })

  it('searches by title and description through the header search box', async () => {
    const queries: (string | null)[] = []
    server.use(
      http.get('/api/v1/collection', ({ request }) => {
        const q = new URL(request.url).searchParams.get('q')
        queries.push(q)
        return HttpResponse.json(page(q ? [] : [card({ title: 'Opera' })]))
      }),
      ...baseHandlers(),
    )
    const { user } = renderApp('/collection')
    await screen.findByRole('article', { name: 'Opera' })
    await user.type(screen.getByRole('searchbox', { name: 'Search cards' }), 'garden')
    await waitFor(() => expect(queries).toContain('garden'))
    expect(await screen.findByText('Nothing matches these filters')).toBeInTheDocument()
  })

  it('shows an empty state that invites adding the first card', async () => {
    server.use(...baseHandlers({ collection: [] }))
    const { user } = renderApp('/collection')
    await user.click(await screen.findByRole('button', { name: /Add your first card/ }))
    expect(await screen.findByRole('dialog', { name: 'Add a card' })).toBeInTheDocument()
  })

  it('shows an error state with retry when the API fails', async () => {
    let fail = true
    server.use(
      http.get('/api/v1/collection', () =>
        fail
          ? HttpResponse.json(
              { status: 500, code: 'internal_error', detail: 'Something went wrong on our side' },
              { status: 500 },
            )
          : HttpResponse.json(page([card({ title: 'Back again' })])),
      ),
      ...baseHandlers(),
    )
    const { user } = renderApp('/collection')
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong on our side')
    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('article', { name: 'Back again' })).toBeInTheDocument()
  })

  it('removes a card with an undo action instead of a blocking confirmation', async () => {
    const calls: string[] = []
    server.use(
      http.delete('/api/v1/collection/:id', ({ params }) => {
        calls.push('remove ' + params.id)
        return new HttpResponse(null, { status: 204 })
      }),
      http.put('/api/v1/collection/:id', ({ params }) => {
        calls.push('save ' + params.id)
        return HttpResponse.json(card({ id: String(params.id) }), { status: 201 })
      }),
      ...baseHandlers({ collection: [card({ id: 'opera', title: 'Opera' })] }),
    )
    const { user } = renderApp('/collection')
    await user.click(await screen.findByRole('button', { name: 'More actions for Opera' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Remove from collection' }))
    expect(await screen.findByText('Removed from collection')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Undo' }))
    await waitFor(() => expect(calls).toEqual(['remove opera', 'save opera']))
  })
})
