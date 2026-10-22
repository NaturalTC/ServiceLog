import { apiFetch } from './client'

export type EquipmentType =
  | 'OIL_BURNER'
  | 'BOILER'
  | 'FURNACE'
  | 'CENTRAL_AC'
  | 'HEAT_PUMP'
  | 'MINI_SPLIT_OUTDOOR'
  | 'MINI_SPLIT_INDOOR'
  | 'WATER_HEATER'
  | 'OTHER'

export interface Equipment {
  id: number
  propertyId: number
  type: EquipmentType
  typeLabel: string
  brand: string | null
  model: string | null
  serialNumber: string | null
  installDate: string | null // YYYY-MM-DD
  notes: string | null
}

// The equipment page also shows where it is and whose it is
export interface EquipmentDetail {
  equipment: Equipment
  customerId: number
  customerName: string
  address: string
}

export interface EquipmentInput {
  type: EquipmentType
  brand: string | null
  model: string | null
  serialNumber: string | null
  installDate: string | null
  notes: string | null
}

export interface EquipmentTypeOption {
  value: EquipmentType
  label: string
}

const json = (method: string, body: object): RequestInit => ({ method, body: JSON.stringify(body) })

export const equipmentApi = {
  types: () => apiFetch<EquipmentTypeOption[]>('/api/equipment-types'),
  listForCustomer: (customerId: number) => apiFetch<Equipment[]>(`/api/customers/${customerId}/equipment`),
  get: (id: number) => apiFetch<EquipmentDetail>(`/api/equipment/${id}`),
  create: (propertyId: number, input: EquipmentInput) =>
    apiFetch<EquipmentDetail>(`/api/properties/${propertyId}/equipment`, json('POST', input)),
  update: (id: number, input: EquipmentInput) => apiFetch<EquipmentDetail>(`/api/equipment/${id}`, json('PUT', input)),
}
