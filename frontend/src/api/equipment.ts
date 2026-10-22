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
  warrantyRegistered: boolean
  warrantyRegisteredOn: string | null
  warrantyEndsOn: string | null
  extendedWarrantyEndsOn: string | null
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
  warrantyRegistered: boolean
  warrantyRegisteredOn: string | null
  warrantyEndsOn: string | null
  extendedWarrantyEndsOn: string | null
}

// An installed unit whose warranty isn't registered with the manufacturer yet
export interface UnregisteredUnit {
  equipmentId: number
  typeLabel: string
  brand: string | null
  model: string | null
  serialNumber: string | null
  installDate: string
  customerId: number
  customerName: string
  address: string
}

export interface EquipmentTypeOption {
  value: EquipmentType
  label: string
}

// A unit whose warranty cover ends soon: a chance to offer an extended warranty or a renewal
export interface ExpiringWarranty {
  equipmentId: number
  typeLabel: string
  brand: string | null
  model: string | null
  serialNumber: string | null
  offer: 'EXTENDED_WARRANTY' | 'RENEWAL'
  endsOn: string
  daysLeft: number
  offeredOn: string | null
  customerId: number
  customerName: string
  customerPhone: string
  customerEmail: string | null
  address: string
}

export interface ExpiringWarranties {
  windowDays: number
  units: ExpiringWarranty[]
}

const json = (method: string, body: object): RequestInit => ({ method, body: JSON.stringify(body) })

export const equipmentApi = {
  types: () => apiFetch<EquipmentTypeOption[]>('/api/equipment-types'),
  listForCustomer: (customerId: number) => apiFetch<Equipment[]>(`/api/customers/${customerId}/equipment`),
  unregistered: () => apiFetch<UnregisteredUnit[]>('/api/equipment/unregistered'),
  warrantyEnding: () => apiFetch<ExpiringWarranties>('/api/equipment/warranty-ending'),
  markOffered: (id: number, offered: boolean) =>
    apiFetch<void>(`/api/equipment/${id}/warranty-offer`, { method: offered ? 'PUT' : 'DELETE' }),
  get: (id: number) => apiFetch<EquipmentDetail>(`/api/equipment/${id}`),
  create: (propertyId: number, input: EquipmentInput) =>
    apiFetch<EquipmentDetail>(`/api/properties/${propertyId}/equipment`, json('POST', input)),
  update: (id: number, input: EquipmentInput) => apiFetch<EquipmentDetail>(`/api/equipment/${id}`, json('PUT', input)),
}

// What the form sends back for a unit as it is now (to change one thing and save)
export function toInput(e: Equipment): EquipmentInput {
  return {
    type: e.type,
    brand: e.brand,
    model: e.model,
    serialNumber: e.serialNumber,
    installDate: e.installDate,
    notes: e.notes,
    warrantyRegistered: e.warrantyRegistered,
    warrantyRegisteredOn: e.warrantyRegisteredOn,
    warrantyEndsOn: e.warrantyEndsOn,
    extendedWarrantyEndsOn: e.extendedWarrantyEndsOn,
  }
}
