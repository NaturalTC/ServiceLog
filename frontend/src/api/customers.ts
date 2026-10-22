import { apiFetch } from './client'

export interface Property {
  id: number | null // null for a property that hasn't been saved yet
  street: string
  unit: string | null
  city: string
  state: string
  zip: string
  notes: string | null
}

export interface Customer {
  id: number
  name: string
  phone: string // normalized: +1XXXXXXXXXX
  email: string | null
  notes: string | null
  properties: Property[]
  remindersOptedOutAt: string | null // when they unsubscribed from reminder emails
}

export type CustomerInput = Omit<Customer, 'id' | 'remindersOptedOutAt'>

const json = (method: string, body: object): RequestInit => ({ method, body: JSON.stringify(body) })

export const customersApi = {
  list: (signal?: AbortSignal) => apiFetch<Customer[]>('/api/customers', { signal }),
  // Matches name, street or phone; every word must match. Returns up to 50 results.
  search: (query: string, signal?: AbortSignal) =>
    apiFetch<Customer[]>(`/api/customers?q=${encodeURIComponent(query)}`, { signal }),
  get: (id: number) => apiFetch<Customer>(`/api/customers/${id}`),
  create: (input: CustomerInput) => apiFetch<Customer>('/api/customers', json('POST', input)),
  update: (id: number, input: CustomerInput) => apiFetch<Customer>(`/api/customers/${id}`, json('PUT', input)),
  // Only when a customer who unsubscribed asks to get reminders again
  resumeReminders: (id: number) => apiFetch<Customer>(`/api/customers/${id}/reminders/resume`, { method: 'POST' }),
}
