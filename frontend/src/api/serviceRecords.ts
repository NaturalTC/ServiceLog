import { apiFetch } from './client'

export type ServiceKind = 'TUNE_UP' | 'REPAIR' | 'INSTALLATION' | 'INSPECTION' | 'OTHER'

export interface ServiceRecord {
  id: number
  equipmentId: number
  serviceDate: string // YYYY-MM-DD
  kind: ServiceKind
  kindLabel: string
  workDone: string
  partsUsed: string | null
  notes: string | null
  followUpNeeded: boolean
  followUpNote: string | null
}

// The record page also shows which unit and whose it is
export interface ServiceRecordDetail {
  record: ServiceRecord
  equipmentLabel: string
  customerId: number
  customerName: string
  address: string
}

export interface ServiceRecordInput {
  serviceDate: string | null // null means today (in the business's time zone)
  kind: ServiceKind
  workDone: string
  partsUsed: string | null
  notes: string | null
  followUpNeeded: boolean
  followUpNote: string | null
}

export interface ServiceKindOption {
  value: ServiceKind
  label: string
}

const json = (method: string, body: object): RequestInit => ({ method, body: JSON.stringify(body) })

export const serviceRecordsApi = {
  kinds: () => apiFetch<ServiceKindOption[]>('/api/service-kinds'),
  get: (id: number) => apiFetch<ServiceRecordDetail>(`/api/service-records/${id}`),
  create: (equipmentId: number, input: ServiceRecordInput) =>
    apiFetch<ServiceRecordDetail>(`/api/equipment/${equipmentId}/service-records`, json('POST', input)),
  update: (id: number, input: ServiceRecordInput) =>
    apiFetch<ServiceRecordDetail>(`/api/service-records/${id}`, json('PUT', input)),
}
