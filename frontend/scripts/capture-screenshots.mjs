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
  ['equipment', `/equipment/${ids.boiler}`],
]
for (const [name, path] of shots) {
  await page.goto(`${base}${path}`)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(300)
  await page.screenshot({ path: `${out}${name}.png` })
  console.log(`captured ${name}`)
}

// The AI box filling in a service record from a short note. Needs OPENAI_API_KEY on the
// backend; the note is made up and customer details are redacted before sending.
await page.goto(`${base}/equipment/${ids.boiler}/service-records/new`)
await page.fill('#quick-note', 'annual on the boiler, replaced the lwco probe, CO 12. expansion tank waterlogged, quote a new one')
await page.click('text=Fill in with AI')
await page.waitForSelector('.ai-box [role=status]', { timeout: 60_000 })
// Scroll so the note sits at the top and the fields AI filled in show below it
await page.locator('#quick-note').evaluate((el) => window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 64))
await page.waitForTimeout(300)
await page.screenshot({ path: `${out}service-note.png` })
console.log('captured service-note')

await browser.close()
