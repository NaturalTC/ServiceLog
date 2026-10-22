import { useSyncExternalStore } from 'react'
import { ApiError, tokenStore } from '../api/client'
import { offlineStore, QUEUE } from './offlineStore'

// Actions taken without signal, kept on the phone and sent in order when the signal comes
// back (S21). Each carries an Idempotency-Key, so if the signal drops mid-send and it goes
// again, the server returns its earlier reply instead of saving the record twice.

export type QueuedKind = 'service-record' | 'start-job' | 'finish-job'

export interface QueuedAction {
  id: string // also the Idempotency-Key
  kind: QueuedKind
  label: string // what the owner sees, e.g. "Service record · Boiler"
  method: 'POST' | 'PUT'
  path: string
  body: unknown
  createdAt: string
  // For a service record: photos to upload once it exists
  photos?: Blob[]
  equipmentId?: number
  jobId?: number
  // Set when the record was created but its photos haven't gone up yet
  recordId?: number
  // The server refused it (e.g. the job was already finished): kept for Retry or Discard
  error?: string
}

let items: QueuedAction[] = []
let loaded = false
let syncing = false
const listeners = new Set<() => void>()

function publish(next: QueuedAction[]) {
  items = next
  listeners.forEach((l) => l())
}

async function reload() {
  const all = await offlineStore.all<QueuedAction>(QUEUE).catch(() => [])
  loaded = true
  publish(all.sort((a, b) => a.createdAt.localeCompare(b.createdAt)))
}

export const syncQueue = {
  async add(action: Omit<QueuedAction, 'id' | 'createdAt'>) {
    const item: QueuedAction = { ...action, id: newKey(), createdAt: new Date().toISOString() }
    await offlineStore.put(QUEUE, item)
    await reload()
    void syncQueue.sync()
  },

  async discard(id: string) {
    await offlineStore.delete(QUEUE, id)
    await reload()
  },

  async retry(id: string) {
    const item = items.find((i) => i.id === id)
    if (item) await offlineStore.put(QUEUE, { ...item, error: undefined })
    await reload()
    void syncQueue.sync()
  },

  async clear() {
    await offlineStore.clear(QUEUE).catch(() => {})
    await reload()
  },

  // Sends everything waiting, oldest first. Stops at the first sign of no signal and
  // tries again later; a refusal from the server is kept with its message.
  async sync() {
    if (!loaded) await reload()
    if (syncing || !tokenStore.get()) return
    syncing = true
    let sent = 0
    try {
      for (const item of items.filter((i) => !i.error)) {
        const outcome = await send(item)
        if (outcome === 'offline') break
        if (outcome === 'sent') sent++
      }
    } finally {
      syncing = false
      await reload()
    }
    if (sent > 0) window.dispatchEvent(new CustomEvent('servicelog:synced', { detail: sent }))
  },

  subscribe(listener: () => void) {
    listeners.add(listener)
    if (!loaded) void reload()
    return () => listeners.delete(listener)
  },

  get: () => items,
}

// crypto.randomUUID() only exists on HTTPS; testing on a phone over the local network is plain HTTP
function newKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('').replace(/(.{8})(.{4})(.{4})(.{4})/, '$1-$2-$3-$4-')
}

async function send(item: QueuedAction): Promise<'sent' | 'offline' | 'refused'> {
  try {
    let recordId = item.recordId
    if (recordId === undefined) {
      const reply = await request(item.method, item.path, JSON.stringify(item.body), item.id)
      recordId = (reply as { record?: { id: number } } | null)?.record?.id
    }
    if (item.photos?.length && recordId !== undefined) {
      // Remember the record first, so a dropped signal here doesn't create it again
      await offlineStore.put(QUEUE, { ...item, recordId })
      const form = new FormData()
      item.photos.forEach((photo, i) => form.append('files', photo, `photo-${i + 1}.jpg`))
      await request('POST', `/api/service-records/${recordId}/photos`, form, `${item.id}-photos`)
    }
    await offlineStore.delete(QUEUE, item.id)
    return 'sent'
  } catch (err) {
    // 409 IN_PROGRESS: the server is still handling an earlier send; try again later
    if (!(err instanceof ApiError) || (err.status === 409 && err.code === 'IN_PROGRESS') || err.status === 401) {
      return 'offline'
    }
    await offlineStore.put(QUEUE, { ...item, error: err.message })
    return 'refused'
  }
}

async function request(method: string, path: string, body: BodyInit, key: string): Promise<unknown> {
  const headers: Record<string, string> = { Accept: 'application/json', 'Idempotency-Key': key }
  if (!(body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(path, { method, headers, body, signal: AbortSignal.timeout(30_000) })
  if (!res.ok) {
    const data = await res.json().catch(() => null)
    throw new ApiError(res.status, data?.message ?? `Request failed (${res.status})`, data?.code ?? null, data)
  }
  return res.status === 204 ? null : res.json()
}

// Try whenever the signal may be back: the app opening, the phone saying it's online, a
// page loading fine again, and every 30 seconds while something is waiting
export function startSyncing() {
  const trySync = () => void syncQueue.sync()
  window.addEventListener('online', trySync)
  window.addEventListener('servicelog:back-online', trySync)
  const timer = setInterval(() => {
    if (items.some((i) => !i.error)) trySync()
  }, 30_000)
  trySync()
  return () => {
    window.removeEventListener('online', trySync)
    window.removeEventListener('servicelog:back-online', trySync)
    clearInterval(timer)
  }
}

export function useSyncQueue(): QueuedAction[] {
  return useSyncExternalStore(syncQueue.subscribe, syncQueue.get)
}

// A job as it will be once what's waiting has synced: started or finished on this phone
export function pendingStatus(jobId: number, queue: QueuedAction[]): 'IN_PROGRESS' | 'DONE' | null {
  const forJob = queue.filter((q) => q.jobId === jobId && !q.error)
  if (forJob.some((q) => q.kind === 'finish-job')) return 'DONE'
  if (forJob.some((q) => q.kind === 'start-job')) return 'IN_PROGRESS'
  return null
}
