// Seeds the demo account with real, source-verified example cards (facts checked on 2026-10-25; see
// demo-cards.json). Covers come from the app's own Wikimedia Commons search, with attribution.
// Usage: BASE_URL=http://localhost:3000 DEMO_PASSWORD=... node scripts/seed-demo.mjs
import { readFile } from 'node:fs/promises'

const base = process.env.BASE_URL ?? 'http://localhost:3000'
const email = process.env.DEMO_EMAIL ?? 'demo@almanook.app'
const password = process.env.DEMO_PASSWORD
if (!password) throw new Error('Set DEMO_PASSWORD (ALMANOOK_DEMO_USER_PASSWORD from .env)')

const jar = new Map()
async function call(method, path, body) {
  const headers = { Accept: 'application/json', Cookie: [...jar].map(([k, v]) => `${k}=${v}`).join('; ') }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (jar.has('XSRF-TOKEN')) headers['X-XSRF-TOKEN'] = jar.get('XSRF-TOKEN')
  const res = await fetch(base + '/api/v1' + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  for (const c of res.headers.getSetCookie()) {
    const [pair] = c.split(';')
    const [k, ...v] = pair.split('=')
    jar.set(k, v.join('='))
  }
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) throw Object.assign(new Error(`${method} ${path} -> ${res.status} ${text}`), { status: res.status, data })
  return data
}

await call('GET', '/auth/csrf')
await call('POST', '/auth/login', { email, password })
await call('GET', '/auth/csrf')

const items = JSON.parse(await readFile(new URL('./demo-cards.json', import.meta.url), 'utf8')).filter((i) => !i.skip)
for (const item of items) {
  let cover = null
  try {
    const [first] = await call('POST', '/images/candidates', { query: item.coverQuery })
    if (first) cover = { source: first.source, url: first.url, attribution: first.attribution, attributionUrl: first.attributionUrl, license: first.license }
  } catch (e) {
    console.warn('no cover for', item.card.title, e.message)
  }
  try {
    await call('POST', '/cards', {
      card: { ...item.card, cover },
      destination: item.destination,
      originalInput: item.card.title,
      enrichment: { mode: 'SEED', sources: item.sources.map((url) => ({ url, title: null })) },
    })
    console.log('✓', item.destination.padEnd(10), item.card.title, cover ? `(${cover.attribution})` : '(no cover)')
  } catch (e) {
    if (e.status === 409) console.log('· exists', item.card.title)
    else throw e
  }
}
