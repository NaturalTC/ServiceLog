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
