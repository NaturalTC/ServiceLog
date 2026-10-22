// Display and validation helpers shared by the customer screens.
// The validation rules mirror the server's (CustomerDtos and PhoneNumbers).

// "+14135551234" or "4135551234" -> "(413) 555-1234"; anything else is returned unchanged
export function formatPhone(phone: string): string {
  const digits = phone.replace(/\D/g, '').replace(/^1(?=\d{10}$)/, '')
  if (digits.length !== 10) return phone
  return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}`
}

export function isValidUsPhone(input: string): boolean {
  const digits = input.replace(/\D/g, '').replace(/^1(?=\d{10}$)/, '')
  return /^[2-9]\d{2}[2-9]\d{6}$/.test(digits)
}

export const isValidEmail = (input: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input)
export const isValidState = (input: string) => /^[A-Za-z]{2}$/.test(input.trim())
export const isValidZip = (input: string) => /^\d{5}(-\d{4})?$/.test(input.trim())

export function formatAddress(p: { street: string; unit: string | null; city: string; state: string; zip: string }) {
  return `${p.street}${p.unit ? `, ${p.unit}` : ''}, ${p.city}, ${p.state} ${p.zip}`
}

// Opens the phone's maps app with directions to the address
export function mapsUrl(address: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`
}

// "2023-06-15" -> "Jun 2023 (3 years old)". Dates are parsed as local dates so they
// don't shift a day in time zones west of UTC.
export function formatInstallDate(isoDate: string, today = new Date()): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const installed = new Date(year, month - 1, day)
  const label = installed.toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
  let age = today.getFullYear() - year
  if (today < new Date(today.getFullYear(), month - 1, day)) age--
  if (age < 1) return `${label} (less than a year old)`
  return `${label} (${age} year${age === 1 ? '' : 's'} old)`
}

// Today's date as YYYY-MM-DD in the device's time zone (for <input type="date" max>)
export function todayIso(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

// "2026-10-21" -> "Wed, Oct 21, 2026" (parsed as a local date, so it never shifts a day)
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

// "2026-10-21" -> "today", "yesterday", "12 days ago", "3 months ago", "2 years ago"
export function daysAgo(isoDate: string, today = new Date()): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const then = new Date(year, month - 1, day)
  const now = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const days = Math.round((now.getTime() - then.getTime()) / 86_400_000)
  if (days <= 0) return 'today'
  if (days === 1) return 'yesterday'
  if (days < 60) return `${days} days ago`
  const months = Math.floor(days / 30.44)
  if (months < 24) return `${months} months ago`
  return `${Math.floor(days / 365.25)} years ago`
}

// "09:30:00" or "09:30" -> "9:30 AM"
export function formatTime(time: string): string {
  const [h, m] = time.split(':').map(Number)
  const suffix = h < 12 ? 'AM' : 'PM'
  const hour = h % 12 === 0 ? 12 : h % 12
  return `${hour}:${String(m).padStart(2, '0')} ${suffix}`
}

// 90 -> "1 hr 30 min", 60 -> "1 hr", 45 -> "45 min"
export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} min`
  return m === 0 ? `${h} hr` : `${h} hr ${m} min`
}

// A YYYY-MM-DD date `days` after the given one, computed on local dates
export function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const d = new Date(year, month - 1, day + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
