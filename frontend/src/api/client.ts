// Small wrapper around fetch for calls to the ServiceLog API.
// Adds the login token to every request and turns error responses into ApiError.

const TOKEN_KEY = 'servicelog.token'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
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

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')

  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(path, { ...options, headers })

  if (res.status === 401 && token) {
    // A stored token was rejected (expired or account removed)
    tokenStore.clear()
    onUnauthorized()
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(res.status, body?.message ?? `Request failed (${res.status})`)
  }

  return res.json() as Promise<T>
}
