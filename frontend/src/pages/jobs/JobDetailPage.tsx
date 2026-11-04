import {
  ArrowCounterClockwise,
  CalendarBlank,
  CheckCircle,
  Clock,
  MapPin,
  NavigationArrow,
  PencilSimple,
  Phone,
  Play,
  User,
  XCircle,
} from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { errorMessage, OfflineError } from '../../api/client'
import { isOpen, jobsApi, type Job } from '../../api/jobs'
import { formatDate, formatDuration, formatPhone, formatTime, mapsUrl, ticketNo } from '../../lib/format'
import { useConfirm } from '../../components/ConfirmSheet'
import { JobTypeTag } from '../../components/ui/JobTypeTag'
import { Loading } from '../../components/Loading'
import { MoreMenu } from '../../components/MoreMenu'
import { Panel } from '../../components/ui/Panel'
import { pendingStatus, syncQueue, useSyncQueue } from '../../lib/syncQueue'

export function JobDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const queue = useSyncQueue()
  const [ask, sheet] = useConfirm()
  // e.g. "finished on your phone" after finishing without signal
  const notice = (useLocation().state as { notice?: string } | null)?.notice

  useEffect(() => {
    jobsApi
      .get(id)
      .then(setJob)
      .catch((err) => setError(errorMessage(err)))
  }, [id])

  async function cancelJob() {
    if (!job) return
    const ok = await ask({
      title: `Cancel the ${job.typeLabel.toLowerCase()} for ${job.customerName}?`,
      message: `${formatDate(job.date)} at ${formatTime(job.startTime)}. It comes off the schedule.`,
      confirmLabel: 'Cancel job',
      cancelLabel: 'Keep job',
      danger: true,
    })
    if (!ok) return
    setBusy(true)
    try {
      await jobsApi.cancel(job.id)
      toast.success(`Cancelled: ${job.typeLabel} for ${job.customerName}`)
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
      toast.success('Job started', { action: { label: 'Undo', onClick: () => void undoStart() } })
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
        <div>
          <p className="ticket-no">{ticketNo(job.id)}</p>
          <h1>
            <JobTypeTag type={job.type} label={job.typeLabel} large />
          </h1>
        </div>
        <span className="tags">
          {shown.status === 'IN_PROGRESS' && <span className="badge now">In progress</span>}
          {shown.status === 'DONE' && <span className="stamp large">Done</span>}
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

      {/* Grouped: when, who, where, notes. The one next step stays pinned at the bottom */}
      <Panel title="When">
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
        </ul>
      </Panel>

      <Panel title="Customer">
        <ul className="fact-list">
          <li>
            <User size={18} aria-hidden="true" />
            <Link to={`/customers/${job.customerId}`} className="tap-link">
              {job.customerName}
            </Link>
          </li>
        </ul>
        <a href={`tel:${job.customerPhone}`} className="button call">
          <Phone size={18} aria-hidden="true" />
          Call {formatPhone(job.customerPhone)}
        </a>
      </Panel>

      <Panel title="Where">
        <ul className="fact-list">
          <li>
            <MapPin size={18} aria-hidden="true" />
            <span>{job.address}</span>
          </li>
        </ul>
        <a href={mapsUrl(job.address)} target="_blank" rel="noreferrer" className="button go">
          <NavigationArrow size={18} aria-hidden="true" />
          Directions
        </a>
      </Panel>

      {job.notes && (
        <Panel title="Notes">
          <p className="notes note-card">{job.notes}</p>
        </Panel>
      )}

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {open && (
        <div className="action-bar">
          {shown.status === 'SCHEDULED' ? (
            <button type="button" onClick={startJob} disabled={busy}>
              <Play size={18} aria-hidden="true" />
              {busy ? 'Starting…' : 'Start Job'}
            </button>
          ) : (
            <Link to={`/jobs/${job.id}/finish`} className="button">
              <CheckCircle size={18} aria-hidden="true" />
              Finish Job
            </Link>
          )}
          <MoreMenu
            title={`${job.typeLabel} · ${job.customerName}`}
            items={[
              { label: 'Reschedule or edit', icon: PencilSimple, to: `/jobs/${job.id}/edit` },
              ...(shown.status === 'IN_PROGRESS'
                ? [{ label: 'Undo start (started by mistake)', icon: ArrowCounterClockwise, onSelect: () => void undoStart() }]
                : []),
              { label: 'Cancel job', icon: XCircle, onSelect: () => void cancelJob(), danger: true },
            ]}
          />
        </div>
      )}

      {!open && (
        <p className="links">
          <Link to="/jobs">Back to jobs</Link>
        </p>
      )}
      {sheet}
    </main>
  )
}
