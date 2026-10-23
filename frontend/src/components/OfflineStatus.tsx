import { ArrowsClockwise, CloudSlash, Warning } from '@phosphor-icons/react'
import { Link } from 'react-router'
import { useConnection } from '../lib/connection'
import { formatDateTime } from '../lib/format'
import { useSyncQueue } from '../lib/syncQueue'

// Under the header on every screen: whether there's signal, and anything not yet sent (S21)
export function OfflineStatus() {
  const { online, savedAt } = useConnection()
  const queue = useSyncQueue()
  const refused = queue.filter((q) => q.error).length
  const waiting = queue.length - refused

  if (online && queue.length === 0) return null

  return (
    <div className="offline-status" role="status">
      {!online && (
        <p className="offline-banner">
          <CloudSlash size={18} aria-hidden="true" />
          <span>
            No signal. Showing what's saved on this phone
            {savedAt ? ` (saved ${formatDateTime(savedAt)})` : ''}.
          </span>
        </p>
      )}
      {queue.length > 0 && (
        <Link to="/sync" className={`sync-pill${refused > 0 ? ' refused' : ''}`}>
          {refused > 0 ? <Warning size={16} aria-hidden="true" /> : <ArrowsClockwise size={16} aria-hidden="true" />}
          {waiting > 0 && `${waiting} waiting to sync`}
          {waiting > 0 && refused > 0 && ' · '}
          {refused > 0 && `${refused} couldn't sync`}
        </Link>
      )}
    </div>
  )
}
