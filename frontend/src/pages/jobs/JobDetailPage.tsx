import { CalendarBlank, CheckCircle, Clock, MapPin, NavigationArrow, Phone, Play, User } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { errorMessage, OfflineError } from '../../api/client'
import { isOpen, jobsApi, type Job } from '../../api/jobs'
import { formatDate, formatDuration, formatPhone, formatTime, mapsUrl } from '../../lib/format'
import { Loading } from '../../components/Loading'
import { pendingStatus, syncQueue, useSyncQueue } from '../../lib/syncQueue'

export function JobDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const queue = useSyncQueue()
  // e.g. "finished on your phone" after finishing without signal
  const notice = (useLocation().state as { notice?: string } | null)?.notice

  useEffect(() => {
    jobsApi
      .get(id)
      .then(setJob)
      .catch((err) => setError(errorMessage(err)))
  }, [id])

  async function cancelJob() {
    if (!job || !window.confirm(`Cancel the ${job.typeLabel.toLowerCase()} for ${job.customerName}?`)) return
    setBusy(true)
    try {
      await jobsApi.cancel(job.id)
      navigate('/jobs', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  async function undoStart() {
    if (!job) return
    setBusy(true)
    // A start that hasn't synced yet is simply dropped
    const queuedStart = queue.find((q) => q.jobId === job.id && q.kind === 'start-job')
    if (queuedStart) {
      await syncQueue.discard(queuedStart.id)
      setBusy(false)
      return
    }
    try {
      setJob(await jobsApi.undoStart(job.id))
    } catch (err) {
      setError(errorMessage(err))
    }
    setBusy(false)
  }

  async function startJob() {
    if (!job) return
    setBusy(true)
    try {
      setJob(await jobsApi.start(job.id))
    } catch (err) {
      // No signal: keep the start on the phone; it syncs by itself later
      if (err instanceof OfflineError) {
        await syncQueue.add({
          kind: 'start-job',
          label: `Start job · ${job.customerName}`,
          method: 'POST',
          path: `/api/jobs/${job.id}/start`,
          body: undefined,
          jobId: job.id,
        })
      } else {
        setError(errorMessage(err))
      }
    }
    setBusy(false)
  }

  if (error && !job) {
    return (
      <main>
        <p className="error" role="alert">
          {error}
        </p>
        <Link to="/jobs">Back to jobs</Link>
      </main>
    )
  }
  if (!job) return <Loading />

  // As it will be once anything done on this phone without signal has synced
  const unsynced = pendingStatus(job.id, queue)
  const shown = unsynced ? { ...job, status: unsynced } : job
  const cancelled = shown.status === 'CANCELLED'
  const open = isOpen(shown)

  return (
    <main>
      <div className="page-title">
        <h1>{job.typeLabel}</h1>
        <span className="tags">
          {shown.status === 'IN_PROGRESS' && <span className="badge now">In progress</span>}
          {shown.status === 'DONE' && <span className="badge done">Done</span>}
          {job.urgency === 'URGENT' && open && <span className="badge urgent">Urgent</span>}
        </span>
      </div>
      {cancelled && <p className="error">This job was cancelled.</p>}
      {unsynced && (
        <p className="notice" role="status">
          {notice ?? `${unsynced === 'DONE' ? 'Finished' : 'Started'} on this phone without signal. It will sync by itself.`}{' '}
          <Link to="/sync">See what's waiting</Link>
        </p>
      )}
      {job.status === 'DONE' && (
        <p className="notice" role="status">
          Finished. Service records were saved on the equipment worked on; see the{' '}
          <Link to={`/customers/${job.customerId}`}>customer's equipment</Link>.
        </p>
      )}

      {/* Key facts first: when, who, where */}
      <ul className="fact-list">
        <li>
          <CalendarBlank size={18} aria-hidden="true" />
          <strong>{formatDate(job.date)}</strong>
        </li>
        <li>
          <Clock size={18} aria-hidden="true" />
          <span className="time">
            {formatTime(job.startTime)} · {formatDuration(job.durationMinutes)}
          </span>
        </li>
        <li>
          <User size={18} aria-hidden="true" />
          <Link to={`/customers/${job.customerId}`}>{job.customerName}</Link>
        </li>
        <li>
          <MapPin size={18} aria-hidden="true" />
          <span>{job.address}</span>
        </li>
      </ul>

      <div className="actions">
        <a href={`tel:${job.customerPhone}`} className="button secondary">
          <Phone size={18} aria-hidden="true" />
          Call {formatPhone(job.customerPhone)}
        </a>
        <a href={mapsUrl(job.address)} target="_blank" rel="noreferrer" className="button secondary">
          <NavigationArrow size={18} aria-hidden="true" />
          Directions
        </a>
      </div>

      {job.notes && (
        <>
          <h2>Notes</h2>
          <p className="notes note-card">{job.notes}</p>
        </>
      )}

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {open && (
        <>
          {/* One clear next step: Start a scheduled job, Finish one in progress */}
          <div className="actions spaced">
            {shown.status === 'SCHEDULED' ? (
              <button type="button" onClick={startJob} disabled={busy}>
                <Play size={18} aria-hidden="true" />
                {busy ? 'Starting…' : 'Start job'}
              </button>
            ) : (
              <Link to={`/jobs/${job.id}/finish`} className="button">
                <CheckCircle size={18} aria-hidden="true" />
                Finish job
              </Link>
            )}
          </div>
          {shown.status === 'SCHEDULED' && (
            <p className="center">
              <Link to={`/jobs/${job.id}/finish`}>Already done? Finish without starting</Link>
            </p>
          )}
          {shown.status === 'IN_PROGRESS' && (
            <p className="center">
              <button type="button" className="link-button" onClick={undoStart} disabled={busy}>
                Started by mistake? Undo start
              </button>
            </p>
          )}
          <div className="actions spaced">
            <Link to={`/jobs/${job.id}/edit`} className="button secondary">
              Reschedule or edit
            </Link>
            <button type="button" className="secondary danger" onClick={cancelJob} disabled={busy}>
              Cancel job
            </button>
          </div>
        </>
      )}

      <p className="links">
        <Link to="/jobs">Back to jobs</Link>
      </p>
    </main>
  )
}
