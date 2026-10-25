import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { baseHandlers } from '../test/fixtures'
import { renderApp } from '../test/render'
import { server } from '../test/server'
import { THEME_STORAGE_KEY } from './ThemeProvider'

function mockSystemPrefersLight(light: boolean) {
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        matches: light && query.includes('light'),
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      }) as MediaQueryList,
  )
}

describe('theme', () => {
  it('is dark by default when the OS has no light preference', async () => {
    mockSystemPrefersLight(false)
    server.use(...baseHandlers())
    renderApp('/collection')
    await screen.findByRole('heading', { level: 1, name: 'My Collection' })
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('follows the OS preference while no explicit choice is saved', async () => {
    mockSystemPrefersLight(true)
    server.use(...baseHandlers())
    renderApp('/collection')
    await screen.findByRole('heading', { level: 1, name: 'My Collection' })
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('toggles every screen and persists the explicit choice', async () => {
    mockSystemPrefersLight(false)
    server.use(...baseHandlers())
    const { user, unmount } = renderApp('/collection')
    await user.click(await screen.findByRole('button', { name: 'Switch to light theme' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
    unmount()

    renderApp('/discover')
    await screen.findByRole('heading', { level: 1, name: 'Discover' })
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('light'))
    expect(screen.getByRole('button', { name: 'Switch to dark theme' })).toBeInTheDocument()
  })
})
