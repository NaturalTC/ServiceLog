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
