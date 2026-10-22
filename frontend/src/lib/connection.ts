import { useSyncExternalStore } from 'react'

// Whether the server can be reached right now, judged by the last request rather than
// navigator.onLine (a phone with one bar in a basement says it's online). When offline,
// `savedAt` is when the oldest saved copy on screen was fetched.
export interface Connection {
  online: boolean
  savedAt: string | null
}

let state: Connection = { online: true, savedAt: null }
const listeners = new Set<() => void>()

export const connection = {
  get: () => state,
  wentOffline(savedAt: string | null) {
    // Keep the oldest saved time on screen; a failed save (no time) doesn't change it
    const oldest = !savedAt || (state.savedAt && state.savedAt < savedAt) ? state.savedAt : savedAt
    if (state.online || oldest !== state.savedAt) set({ online: false, savedAt: oldest })
  },
  backOnline() {
    if (!state.online) set({ online: true, savedAt: null })
  },
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}

function set(next: Connection) {
  const wasOffline = !state.online
  state = next
  listeners.forEach((l) => l())
  if (wasOffline && next.online) window.dispatchEvent(new Event('servicelog:back-online'))
}

export function useConnection(): Connection {
  return useSyncExternalStore(connection.subscribe, connection.get)
}
