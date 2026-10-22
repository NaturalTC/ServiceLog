// Small wrapper around fetch for calls to the ServiceLog API.
// Adds the login token to every request and turns error responses into ApiError.
// Keeps a copy of every page's data on the phone, and falls back to it without signal.
import { connection } from '../lib/connection'
import { CACHE, offlineStore } from '../lib/offlineStore'

const TOKEN_KEY = 'servicelog.token'

export class ApiError extends Error {
  readonly status: number
  // Machine-readable reason from the API, e.g. "EMAIL_NOT_VERIFIED" or "INVALID_TOKEN"
  readonly code: string | null
  // The full error body, for errors that carry extra data (e.g. the jobs a booking overlaps)
  readonly body: unknown

  constructor(status: number, message: string, code: string | null = null, body: unknown = null) {
    super(message)
    this.status = status
    this.code = code
    this.body = body
  }
}

export function errorMessage(err: unknown): string {
  if (err instanceof ApiError || err instanceof OfflineError) return err.message
  return 'Could not reach the server. Check your connection.'
}

// The token lives in localStorage so the owner stays logged in between app opens.
// Trade-off: any script running on the page could read it, so the app must never
// render untrusted HTML. React escapes text by default, which covers that.
export const tokenStore = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

// Called when any request comes back 401, so the app can return to the login screen
let onUnauthorized: () => void = () => {}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

// Thrown when the server can't be reached (no signal, or it's down). Pages that can work
// without signal (adding a service record, starting or finishing a job) catch it and keep
// the action on the phone instead.
export class OfflineError extends Error {
  constructor(message = "No signal. This hasn't been saved on this phone yet.") {
    super(message)
    this.name = 'OfflineError'
  }
}

// Give up on a request after this long: one bar of signal can leave it hanging for minutes
const TIMEOUT_MS = 15_000

interface Saved {
  data: unknown
  savedAt: string
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  let res: Response
  try {
    res = await send(path, options)
  } catch (err) {
    // The page cancelled it (e.g. a newer search): not a connection problem
    if (options.signal?.aborted) throw err
    if (method === 'GET') return savedCopy<T>(path)
    connection.wentOffline(null)
    throw new OfflineError('No signal, so this couldn’t be saved. Try again when you have signal.')
  }

  // Server down or restarting behind the proxy: treat like no signal
  if (method === 'GET' && [502, 503, 504].includes(res.status)) return savedCopy<T>(path)

  connection.backOnline()

  if (res.status === 401 && tokenStore.get()) {
    // A stored token was rejected (expired or account removed)
    tokenStore.clear()
    onUnauthorized()
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(res.status, body?.message ?? `Request failed (${res.status})`, body?.code ?? null, body)
  }

  // 204 No Content: nothing to parse
  if (res.status === 204) return undefined as T
  const data = (await res.json()) as T
  // Keep a copy of every page's data, so it still opens without signal
  if (method === 'GET') {
    offlineStore.put(CACHE, { data, savedAt: new Date().toISOString() } satisfies Saved, path).catch(() => {})
  }
  return data
}

function send(path: string, options: RequestInit): Promise<Response> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  // Uploads (FormData) set their own multipart Content-Type with the boundary
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  return fetch(path, { ...options, headers, signal: withTimeout(options.signal) })
}

// The page's own cancel signal (if any) plus the timeout. Done by hand because
// AbortSignal.any() needs iOS 17.4+.
function withTimeout(signal?: AbortSignal | null): AbortSignal {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  signal?.addEventListener('abort', () => controller.abort(), { once: true })
  controller.signal.addEventListener('abort', () => clearTimeout(timer), { once: true })
  return controller.signal
}

async function savedCopy<T>(path: string): Promise<T> {
  const saved = await offlineStore.get<Saved>(CACHE, path).catch(() => undefined)
  connection.wentOffline(saved?.savedAt ?? null)
  if (!saved) throw new OfflineError()
  return saved.data as T
}
