import { expect, test, type Page } from '@playwright/test'

function isoDate(offsetDays: number) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function register(page: Page) {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  await page.goto('/login')
  await page.getByRole('button', { name: 'Create an account' }).click()
  await page.getByLabel('Name').fill('E2E Tester')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'My Collection' })).toBeVisible()
}

async function addManualCard(
  page: Page,
  opts: {
    title: string
    type?: string
    city?: string
    seasons?: string[]
    ends?: string
    destination?: 'COLLECTION' | 'DISCOVER'
  },
) {
  await page.getByRole('button', { name: 'Add card' }).click()
  const input = page.getByRole('dialog', { name: 'Add a card' })
  await input.getByLabel('Place, event, link or note').fill(opts.title)
  await input.getByRole('button', { name: 'Create manually' }).click()
  const form = page.getByRole('dialog', { name: 'New card' })
  if (opts.type) await form.getByLabel('Type').selectOption(opts.type)
  if (opts.city) await form.getByLabel('City').fill(opts.city)
  for (const s of opts.seasons ?? []) await form.getByRole('button', { name: s, exact: true }).click()
  if (opts.ends) await form.getByLabel('Ends').fill(opts.ends)
  if (opts.destination) await form.getByLabel('Save to').selectOption(opts.destination)
  await form.getByRole('button', { name: 'Save card' }).click()
  await expect(form).toBeHidden()
}

test('collect, filter, search, archive and restore without any AI key', async ({ page }) => {
  await register(page)
  await expect(page.getByText('Your collection is empty')).toBeVisible()

  await addManualCard(page, {
    title: 'Ogród Botaniczny w Powsinie',
    city: 'Warszawa',
    seasons: ['Spring', 'Autumn'],
  })
  await addManualCard(page, { title: 'Perfect Days', type: 'FILM' })
  await addManualCard(page, {
    title: 'Ended exhibition',
    type: 'EXHIBITION',
    city: 'Kraków',
    ends: isoDate(-1),
  })

  const grid = page.getByRole('region', { name: 'Collection' })
  await expect(grid.getByRole('article')).toHaveCount(2)
  await expect(grid.getByRole('article', { name: 'Ended exhibition' })).toHaveCount(0)

  // Season filter: a single season excludes non-seasonal cards
  await page.getByText('Spring', { exact: true }).first().click()
  await expect(grid.getByRole('article')).toHaveCount(1)
  await expect(grid.getByRole('article', { name: 'Ogród Botaniczny w Powsinie' })).toBeVisible()
  await page.getByText('Non-seasonal', { exact: true }).click()
  await expect(grid.getByRole('article', { name: 'Perfect Days' })).toBeVisible()
  await page.getByRole('button', { name: 'Clear filters' }).click()

  // Search
  await page.getByRole('searchbox', { name: 'Search cards' }).fill('botaniczny')
  await expect(grid.getByRole('article')).toHaveCount(1)
  await page.getByRole('button', { name: 'Clear search' }).click()
  await expect(grid.getByRole('article')).toHaveCount(2)

  // The ended exhibition was archived on creation, then restored
  await page.getByRole('navigation', { name: 'Main' }).first().getByRole('link', { name: 'Expired' }).click()
  const expired = page.getByRole('article', { name: 'Ended exhibition' })
  await expect(expired).toBeVisible()
  await expired.getByRole('button', { name: 'Restore' }).click()
  await expect(page.getByText('No expired events')).toBeVisible()
  await page
    .getByRole('navigation', { name: 'Main' })
    .first()
    .getByRole('link', { name: 'My Collection' })
    .click()
  await expect(page.getByRole('region', { name: 'Collection' }).getByRole('article')).toHaveCount(3)
})

test('Discover inbox: save moves an idea to the collection exactly once', async ({ page }) => {
  await register(page)
  await page.getByRole('navigation', { name: 'Main' }).first().getByRole('link', { name: 'Discover' }).click()
  await addManualCard(page, {
    title: 'Wiener Christkindlmarkt',
    type: 'MARKET',
    city: 'Wien',
    seasons: ['Winter'],
    destination: 'DISCOVER',
  })
  await addManualCard(page, { title: 'Maybe later', destination: 'DISCOVER' })

  const inbox = page.getByRole('region', { name: 'Discover inbox' })
  await inbox.getByRole('button', { name: 'Dismiss Maybe later' }).click()
  await expect(inbox.getByRole('article', { name: 'Maybe later' })).toHaveCount(0)
  await inbox.getByRole('button', { name: 'Save Wiener Christkindlmarkt to collection' }).click()
  await expect(page.getByText('Your inbox is clear')).toBeVisible()

  await page.getByRole('tab', { name: 'Dismissed' }).click()
  await expect(page.getByRole('article', { name: 'Maybe later' })).toBeVisible()

  await page
    .getByRole('navigation', { name: 'Main' })
    .first()
    .getByRole('link', { name: 'My Collection' })
    .click()
  await expect(
    page
      .getByRole('region', { name: 'Collection' })
      .getByRole('article', { name: 'Wiener Christkindlmarkt' }),
  ).toHaveCount(1)
})

test('theme choice applies everywhere and survives a reload', async ({ page }) => {
  await register(page)
  const html = page.locator('html')
  await expect(html).toHaveAttribute('data-theme', /dark|light/)
  const before = await html.getAttribute('data-theme')
  const target = before === 'dark' ? 'light' : 'dark'
  await page.getByRole('button', { name: `Switch to ${target} theme` }).click()
  await expect(html).toHaveAttribute('data-theme', target)
  await page.reload()
  await expect(html).toHaveAttribute('data-theme', target)
  await page.getByRole('navigation', { name: 'Main' }).first().getByRole('link', { name: 'Settings' }).click()
  await expect(html).toHaveAttribute('data-theme', target)
  await expect(page.getByRole('radio', { name: target === 'dark' ? 'Dark' : 'Light' })).toBeChecked()
})
