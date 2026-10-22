import { RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { useConnection } from '../../lib/connection'
import { formatDateTime } from '../../lib/format'
import { syncQueue, useSyncQueue } from '../../lib/syncQueue'

// What's been done without signal and not sent yet, and anything the server refused (S21)
export function SyncPage() {
  const queue = useSyncQueue()
  const { online } = useConnection()
  const [syncing, setSyncing] = useState(false)

  async function syncNow() {
    setSyncing(true)
    await syncQueue.sync()
    setSyncing(false)
  }

  function discard(id: string, label: string) {
    if (window.confirm(`Discard “${label}”? It won't be saved.`)) void syncQueue.discard(id)
  }

  return (
    <main>
      <h1>Waiting to sync</h1>
      <p className="muted">
        Done without signal and kept on this phone. Everything goes to the server by itself when there's signal again.
      </p>

      {queue.length === 0 && <p className="empty">Everything is synced.</p>}

      <ul className="card-list">
        {queue.map((item) => (
          <li key={item.id} className="card">
            <span className="card-row">
              <strong>{item.label}</strong>
              {item.error ? (
                <span className="badge urgent">Couldn't sync</span>
              ) : (
                <span className="badge now">Waiting</span>
              )}
            </span>
            <span className="muted">
              {formatDateTime(item.createdAt)}
              {item.photos?.length ? ` · ${item.photos.length} photo${item.photos.length === 1 ? '' : 's'}` : ''}
            </span>
            {item.error && <span className="follow-up-tag">{item.error}</span>}
            {item.error && (
              <div className="actions">
                <button type="button" className="secondary danger" onClick={() => discard(item.id, item.label)}>
                  Discard
                </button>
                <button type="button" onClick={() => syncQueue.retry(item.id)}>
                  Try again
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>

      {queue.some((q) => !q.error) && (
        <div className="actions spaced">
          <button type="button" className="dark" onClick={syncNow} disabled={syncing}>
            <RefreshCw size={18} aria-hidden="true" />
            {syncing ? 'Syncing…' : online ? 'Sync now' : 'Try to sync now'}
          </button>
        </div>
      )}
    </main>
  )
}
