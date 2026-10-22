import { apiFetch } from './client'

export interface CustomerCard {
  id: number
  name: string
  phone: string
  address: string | null
}

export interface AssistantReply {
  conversationId: string
  reply: string
  customers: CustomerCard[]
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
