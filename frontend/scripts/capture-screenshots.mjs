// Captures the phone screenshots used on the landing page, from a running app filled
// with demo data (scripts/seed-demo-data.py). Not a project dependency; run with:
//
//   npm i --no-save playwright@1.63.0 && npx playwright install chromium
//   BASE_URL=http://localhost:5173 EMAIL=... PASSWORD=... node scripts/capture-screenshots.mjs
//
// Writes PNGs to public/landing/ (convert to WebP afterwards to keep them small).
import { chromium } from 'playwright'

const base = process.env.BASE_URL ?? 'http://localhost:5173'
const out = new URL('../public/landing/', import.meta.url).pathname

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, colorScheme: 'light' })

// Late morning, so My day shows a finished job, one in progress and the rest of the day
const now = new Date()
await page.clock.setFixedTime(new Date(now.getFullYear(), now.getMonth(), now.getDate(), 11, 30))

await page.goto(`${base}/login`)
await page.fill('#email', process.env.EMAIL)
await page.fill('#password', process.env.PASSWORD)
await page.click('button[type=submit]')
await page.waitForSelector('text=My day')

// Find the customer and unit to show, through the app's own API
const ids = await page.evaluate(async () => {
  const headers = { Authorization: `Bearer ${localStorage.getItem('servicelog.token')}` }
  const customers = await (await fetch('/api/customers?q=Kowalski', { headers })).json()
  const equipment = await (await fetch(`/api/customers/${customers[0].id}/equipment`, { headers })).json()
  return { customer: customers[0].id, boiler: equipment.find((e) => e.type === 'BOILER').id }
})

const shots = [
  ['my-day', '/'],
  ['tune-ups', '/tune-ups'],
  ['customer', `/customers/${ids.customer}`],
  ['equipment', `/equipment/${ids.boiler}`],
]
for (const [name, path] of shots) {
  await page.goto(`${base}${path}`)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(300)
  await page.screenshot({ path: `${out}${name}.png` })
  console.log(`captured ${name}`)
}
await browser.close()
