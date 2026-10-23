import { useEffect, useState } from 'react'
import { Stethoscope } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { equipmentApi, type EquipmentDetail } from '../../api/equipment'
import { serviceRecordsApi, type ServiceRecord } from '../../api/serviceRecords'
import { daysAgo, formatDate, formatDateTime, formatInstallDate, todayIso } from '../../lib/format'
import { useSyncQueue } from '../../lib/syncQueue'
import type { ServiceRecordInput } from '../../api/serviceRecords'
import { Loading } from '../../components/Loading'

// "Jun 15, 2033", or "Ended Jun 15, 2023" once it's past
function warrantyEnd(isoDate: string): string {
  return isoDate < todayIso() ? `Ended ${formatDate(isoDate)}` : formatDate(isoDate)
}

export function EquipmentDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const [detail, setDetail] = useState<EquipmentDetail | null>(null)
  const [history, setHistory] = useState<ServiceRecord[]>([])
  const [error, setError] = useState<string | null>(null)
  // e.g. "saved on your phone" after adding a record without signal
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  // Records added without signal that haven't reached the server yet
  const pending = useSyncQueue().filter((q) => q.kind === 'service-record' && q.equipmentId === id)
  const [synced, setSynced] = useState(0)

  useEffect(() => {
    Promise.all([equipmentApi.get(id), serviceRecordsApi.history(id)])
      .then(([d, h]) => {
        setDetail(d)
        setHistory(h)
      })
      .catch((err) => setError(errorMessage(err)))
  }, [id, synced])

  // Reload once something synced, so it moves from "waiting" into the history
  useEffect(() => {
    const onSynced = () => setSynced((n) => n + 1)
    window.addEventListener('servicelog:synced', onSynced)
    return () => window.removeEventListener('servicelog:synced', onSynced)
  }, [])

  if (error) {
    return (
      <main>
        <p className="error" role="alert">
          {error}
        </p>
        <Link to="/customers">Back to customers</Link>
      </main>
    )
  }
  if (!detail) return <Loading />

  const { equipment: e, customerId, customerName, address } = detail
  const makeModel = [e.brand, e.model].filter(Boolean).join(' ')
  // History comes back newest first, so the first entry is the last visit
  const lastService = history[0]
  const lastTuneUp = history.find((r) => r.kind === 'TUNE_UP')

  return (
    <main>
      <div className="page-title">
        <h1>{e.typeLabel}</h1>
        <Link to={`/equipment/${e.id}/edit`} className="button secondary">
          Edit
        </Link>
      </div>
      <p className="muted">
        <Link to={`/customers/${customerId}`}>{customerName}</Link> · {address}
      </p>

      {/* What the owner wants to know first on site: when was this last looked at? */}
      <div className="summary">
        <div>
          <span className="muted">Last service</span>
          <strong>{lastService ? formatDate(lastService.serviceDate) : 'Never recorded'}</strong>
          {lastService && <span className="muted">{daysAgo(lastService.serviceDate)}</span>}
        </div>
        <div>
          <span className="muted">Last tune-up</span>
          <strong>{lastTuneUp ? formatDate(lastTuneUp.serviceDate) : 'Never recorded'}</strong>
          {lastTuneUp && <span className="muted">{daysAgo(lastTuneUp.serviceDate)}</span>}
        </div>
      </div>

      <div className="actions">
        <Link to={`/equipment/${e.id}/service-records/new`} className="button">
          + Add service record
        </Link>
        <button
          type="button"
          className="secondary"
          onClick={() =>
            navigate('/assistant', {
              state: { ask: `Help me troubleshoot unit #${e.id} (${[e.typeLabel, makeModel].filter(Boolean).join(', ')}): ` },
            })
          }
        >
          <Stethoscope size={18} aria-hidden="true" />
          Troubleshoot
        </button>
      </div>

      <dl className="details">
        <dt>Brand and model</dt>
        <dd>{makeModel || <span className="muted">Not recorded</span>}</dd>
        <dt>Serial number</dt>
        <dd className="mono">{e.serialNumber ?? <span className="muted">Not recorded</span>}</dd>
        <dt>Installed</dt>
        <dd>{e.installDate ? formatInstallDate(e.installDate) : <span className="muted">Unknown</span>}</dd>
        <dt>Warranty</dt>
        <dd>
          {e.warrantyRegistered ? (
            e.warrantyRegisteredOn ? (
              `Registered ${formatDate(e.warrantyRegisteredOn)}`
            ) : (
              'Registered'
            )
          ) : (
            <span className="follow-up-tag">Not registered</span>
          )}
        </dd>
        {e.warrantyEndsOn && (
          <>
            <dt>Warranty ends</dt>
            <dd>{warrantyEnd(e.warrantyEndsOn)}</dd>
          </>
        )}
        {e.extendedWarrantyEndsOn && (
          <>
            <dt>Extended warranty ends</dt>
            <dd>{warrantyEnd(e.extendedWarrantyEndsOn)}</dd>
          </>
        )}
        {e.notes && (
          <>
            <dt>Notes</dt>
            <dd className="notes">{e.notes}</dd>
          </>
        )}
      </dl>

      <h2>Service history</h2>
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {history.length === 0 && pending.length === 0 && <p className="empty">No service recorded yet.</p>}
      <ul className="card-list">
        {pending.map((q) => {
          const input = q.body as ServiceRecordInput
          return (
            <li key={q.id}>
              <Link to="/sync" className="card pending-card">
                <span className="card-row">
                  <strong>{input.serviceDate ? formatDate(input.serviceDate) : `Added ${formatDateTime(q.createdAt)}`}</strong>
                  <span className={q.error ? 'badge urgent' : 'badge now'}>
                    {q.error ? "Couldn't sync" : 'Waiting to sync'}
                  </span>
                </span>
                <span className="clamp">{input.workDone}</span>
              </Link>
            </li>
          )
        })}
        {history.map((r) => (
          <li key={r.id}>
            <Link to={`/service-records/${r.id}`} className="card">
              <span className="card-row">
                <strong>{formatDate(r.serviceDate)}</strong>
                <span className="badge">{r.kindLabel}</span>
              </span>
              <span className="clamp">{r.workDone}</span>
              {r.followUpNeeded && (
                <span className="follow-up-tag">Follow-up needed{r.followUpNote ? `: ${r.followUpNote}` : ''}</span>
              )}
            </Link>
          </li>
        ))}
      </ul>

      <p className="links">
        <Link to={`/customers/${customerId}`}>Back to {customerName}</Link>
      </p>
    </main>
  )
}
