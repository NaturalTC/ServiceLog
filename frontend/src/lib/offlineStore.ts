// The phone's own storage for working without signal (IndexedDB): the last copy of each
// page's data, and the actions waiting to be sent. Wiped on log out.

const DB_NAME = 'servicelog-offline'
const VERSION = 1
export const CACHE = 'cache'
export const QUEUE = 'queue'

let opening: Promise<IDBDatabase> | null = null

function open(): Promise<IDBDatabase> {
  opening ??= new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, VERSION)
    request.onupgradeneeded = () => {
      request.result.createObjectStore(CACHE)
      request.result.createObjectStore(QUEUE, { keyPath: 'id' })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => {
      opening = null
      reject(request.error)
    }
  })
  return opening
}

function run<T>(store: string, mode: IDBTransactionMode, action: (s: IDBObjectStore) => IDBRequest): Promise<T> {
  return open().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const request = action(db.transaction(store, mode).objectStore(store))
        request.onsuccess = () => resolve(request.result as T)
        request.onerror = () => reject(request.error)
      }),
  )
}

export const offlineStore = {
  get: <T>(store: string, key: string) => run<T | undefined>(store, 'readonly', (s) => s.get(key)),
  put: (store: string, value: unknown, key?: string) =>
    run<IDBValidKey>(store, 'readwrite', (s) => (key === undefined ? s.put(value) : s.put(value, key))),
  delete: (store: string, key: string) => run<undefined>(store, 'readwrite', (s) => s.delete(key)),
  all: <T>(store: string) => run<T[]>(store, 'readonly', (s) => s.getAll()),
  clear: (store: string) => run<undefined>(store, 'readwrite', (s) => s.clear()),
}
