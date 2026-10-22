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
}

export const assistantApi = {
  ask: (conversationId: string | null, message: string) =>
    apiFetch<AssistantReply>('/api/assistant/messages', {
      method: 'POST',
      body: JSON.stringify({ conversationId, message }),
    }),
  end: (conversationId: string) =>
    apiFetch<void>(`/api/assistant/conversations/${conversationId}`, { method: 'DELETE' }),
}
