import { apiFetch } from './client'

export interface CustomerCard {
  id: number
  name: string
  phone: string
  address: string | null
}

export interface Detail {
  label: string
  value: string
}

// Prepared by the assistant for the owner to confirm (S34): payload is exactly what the
// app's own form sends; missing lists required fields still to fill in
export interface Proposal {
  type: 'JOB' | 'CUSTOMER' | 'SERVICE_RECORD'
  title: string
  details: Detail[]
  payload: Record<string, unknown>
  missing: string[]
  customerId: number | null
  equipmentId: number | null
  // Set in the chat once handled
  outcome?: { label: string; link: string }
}

export interface AssistantReply {
  conversationId: string
  reply: string
  customers: CustomerCard[]
  proposals: Proposal[]
  // Troubleshooting advice: shown labelled "Suggestions, not a diagnosis"
  suggestions: boolean
}

export const assistantApi = {
  ask: (conversationId: string | null, message: string) =>
    apiFetch<AssistantReply>('/api/assistant/messages', {
      method: 'POST',
      body: JSON.stringify({ conversationId, message }),
    }),
  // "Describe it" (S35): fields for the form, for the owner to check; nothing is saved
  fill: <T>(form: 'customer' | 'job' | 'equipment', text: string) =>
    apiFetch<T>('/api/assistant/fill', { method: 'POST', body: JSON.stringify({ form, text }) }),
  // "Read nameplate" (S36)
  nameplate: (photo: Blob) => {
    const form = new FormData()
    form.append('photo', photo, 'nameplate.jpg')
    return apiFetch<NameplateReading>('/api/assistant/nameplate', { method: 'POST', body: form })
  },
  end: (conversationId: string) =>
    apiFetch<void>(`/api/assistant/conversations/${conversationId}`, { method: 'DELETE' }),
}

// What "Describe it" returns for each form (null: not mentioned)
export interface JobFill {
  customerId: number | null
  customerName: string | null
  propertyId: number | null
  type: string | null
  date: string | null
  startTime: string | null
  durationMinutes: number | null
  urgency: 'NORMAL' | 'URGENT'
  notes: string | null
}

export interface EquipmentFill {
  type: string | null
  brand: string | null
  model: string | null
  serialNumber: string | null
  installDate: string | null
  notes: string | null
}

export interface NameplateReading {
  readable: boolean
  brand: string | null
  model: string | null
  serialNumber: string | null
  type: string | null
  manufactured: string | null // YYYY-MM or YYYY-MM-DD
  details: string[]
  confidence: 'high' | 'medium' | 'low'
}
