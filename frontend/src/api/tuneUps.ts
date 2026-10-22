import { ApiError, apiFetch } from './client'

export type Season = 'HEATING' | 'COOLING'

// A unit due for its seasonal tune-up, with who to call and where
export interface DueUnit {
  equipmentId: number
  equipmentLabel: string
  brand: string | null
  model: string | null
  season: Season
  lastTuneUp: string | null // null: no tune-up or install on record
  customerId: number
  customerName: string
  customerPhone: string
  customerEmail: string | null
  propertyId: number
  address: string
  bookedJobId: number | null // a tune-up already scheduled at this address
  bookedDate: string | null
  lastReminderAt: string | null // ISO timestamp of the last reminder email to this address
}

export interface Reminder {
  id: number
  customerId: number
  propertyId: number
  recipientEmail: string
  subject: string
  body: string
  status: 'SENT' | 'FAILED'
  error: string | null
  sentAt: string
}

// 409 when a reminder already went to this address in the last two weeks
export function recentlyRemindedAt(err: unknown): string | null {
  if (err instanceof ApiError && err.code === 'REMINDER_RECENTLY_SENT') {
    return (err.body as { lastSentAt: string }).lastSentAt
  }
  return null
}

export interface DueList {
  today: string
  heatingSeason: boolean
  coolingSeason: boolean
  intervalMonths: number
  units: DueUnit[]
}

export const tuneUpsApi = {
  due: () => apiFetch<DueList>('/api/tune-ups/due'),
  // Sends one reminder email; only ever called from the owner's tap
  sendReminder: (customerId: number, propertyId: number, resend = false) =>
    apiFetch<Reminder>('/api/tune-ups/reminders', {
      method: 'POST',
      body: JSON.stringify({ customerId, propertyId, resend }),
    }),
  history: (customerId: number) => apiFetch<Reminder[]>(`/api/customers/${customerId}/reminders`),
}
