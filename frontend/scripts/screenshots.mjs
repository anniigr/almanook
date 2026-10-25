// Captures the README screenshots from a running stack seeded with scripts/seed-demo.mjs.
// Usage: BASE_URL=http://localhost:3000 DEMO_PASSWORD=... node scripts/screenshots.mjs ../docs/screenshots
import { chromium } from '@playwright/test'

const base = process.env.BASE_URL ?? 'http://localhost:3000'
const outDir = process.argv[2] ?? '../docs/screenshots'
const browser = await chromium.launch()

async function session(theme, viewport = { width: 1440, height: 900 }) {
  const context = await browser.newContext({ viewport, colorScheme: theme, deviceScaleFactor: 2 })
  await context.addInitScript((t) => localStorage.setItem('almanook.theme', t), theme)
  const page = await context.newPage()
  await page.goto(base + '/login')
  await page.getByLabel('Email').fill(process.env.DEMO_EMAIL ?? 'demo@almanook.app')
  await page.getByLabel('Password').fill(process.env.DEMO_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForURL('**/collection')
  return { context, page }
}

async function settle(page) {
  await page.waitForLoadState('networkidle')
  await page.waitForFunction(() => [...document.images].every((i) => i.complete))
  await page.waitForTimeout(400)
}

async function shot(page, name) {
  await settle(page)
  await page.screenshot({ path: `${outDir}/${name}.png` })
  console.log('saved', name)
}

{
  const { context, page } = await session('dark')
  await shot(page, 'collection-dark')
  await page.getByText('Winter', { exact: true }).first().click()
  await shot(page, 'season-filter-winter')
  await page.goto(base + '/discover')
  await shot(page, 'discover')
  await page.goto(base + '/expired')
  await shot(page, 'expired')
  await page.goto(base + '/collection')
  await page.getByRole('button', { name: 'Rzeźba po stabilności', exact: true }).click()
  await page.getByRole('dialog').waitFor()
  await shot(page, 'card-detail')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Add card' }).click()
  await page.getByLabel('Place, event, link or note').fill('https://www.wroclaw.pl/go/wydarzenia/eventy/1417327-jarmark-bozonarodzeniowy-2026')
  await page.getByRole('button', { name: /Generate card/ }).click()
  await page.getByRole('dialog', { name: 'Review the card' }).waitFor({ timeout: 30000 })
  await shot(page, 'add-card-review')
  await context.close()
}
{
  const { context, page } = await session('light')
  await shot(page, 'collection-light')
  await page.goto(base + '/settings')
  await shot(page, 'settings-light')
  await context.close()
}
{
  const { context, page } = await session('dark', { width: 390, height: 844 })
  await shot(page, 'mobile-dark')
  await context.close()
}
await browser.close()
