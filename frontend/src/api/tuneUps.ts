import { apiFetch } from './client'

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
}
