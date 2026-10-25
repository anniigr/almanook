// Dev helper: sign in and screenshot a page. Usage: node scripts/peek.mjs <path> <out.png> [theme] [width]
import { chromium } from '@playwright/test'

const [, , path = '/collection', out = '/tmp/peek.png', theme = 'dark', width = '1440'] = process.argv
const base = process.env.BASE_URL ?? 'http://localhost:5173'
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: Number(width), height: 900 }, colorScheme: theme })
await page.addInitScript((t) => localStorage.setItem('almanook.theme', t), theme)
await page.goto(base + '/login')
await page.getByLabel('Email').fill(process.env.DEMO_EMAIL ?? 'demo@almanook.app')
await page.getByLabel('Password').fill(process.env.DEMO_PASSWORD)
await page.getByRole('button', { name: 'Sign in' }).click()
await page.waitForURL('**/collection')
await page.goto(base + path)
await page.waitForTimeout(1200)
if (process.env.CLICK) {
  await page.getByRole('button', { name: process.env.CLICK }).first().click()
  await page.waitForTimeout(800)
}
await page.screenshot({ path: out, fullPage: false })
await browser.close()
