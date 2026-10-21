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
}

export type CustomerInput = Omit<Customer, 'id'>

const json = (method: string, body: object): RequestInit => ({ method, body: JSON.stringify(body) })

export const customersApi = {
  list: () => apiFetch<Customer[]>('/api/customers'),
  get: (id: number) => apiFetch<Customer>(`/api/customers/${id}`),
  create: (input: CustomerInput) => apiFetch<Customer>('/api/customers', json('POST', input)),
  update: (id: number, input: CustomerInput) => apiFetch<Customer>(`/api/customers/${id}`, json('PUT', input)),
}
