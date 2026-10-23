import {
  ArrowCounterClockwise,
  CaretRight,
  Check,
  CheckCircle,
  MapTrifold,
  NavigationArrow,
  Phone,
  Play,
  Plus,
  ShieldWarning,
} from '@phosphor-icons/react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { errorMessage, OfflineError } from '../../api/client'
import { equipmentApi } from '../../api/equipment'
import { jobsApi, type Job, type Today } from '../../api/jobs'
import {
  directionsUrl,
  formatDate,
  formatDuration,
  formatPhone,
  formatTime,
  minutesOfDay,
} from '../../lib/format'
import { Loading } from '../../components/Loading'
import { SwipeRow } from '../../components/SwipeRow'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pill } from '../../components/ui/Pill'
import { saveTodayForOffline } from '../../lib/prefetch'
import { pendingStatus, syncQueue, useSyncQueue } from '../../lib/syncQueue'

// The home screen: the day as a route of stops (S39). Done stops are checked and small,
// the current one is the big card with Call, Drive and Start/Finish, the ones ahead are
// outlined and can be swiped (right to call, left to drive).
// "Today" comes from the server (the business's time zone), not the phone's clock.

function nowMinutes(): number {
  const now = new Date()
  return now.getHours() * 60 + now.getMinutes()
}

// The job happening now, or else the next one still to come, or else the first one that
// was missed (finished jobs don't count). That one gets the big card.
function currentOrNext(jobs: Job[], minute: number): { id: number; label: 'Now' | 'Next' | 'Late' } | null {
  jobs = jobs.filter((j) => j.status !== 'DONE')
  const inProgress = jobs.find((j) => j.status === 'IN_PROGRESS')
  if (inProgress) return { id: inProgress.id, label: 'Now' }
  const now = jobs.find((j) => {
    const start = minutesOfDay(j.startTime)
    return start <= minute && minute < start + j.durationMinutes
  })
  if (now) return { id: now.id, label: 'Now' }
  const next = jobs.find((j) => minutesOfDay(j.startTime) > minute)
  if (next) return { id: next.id, label: 'Next' }
  const missed = jobs[0]
  return missed ? { id: missed.id, label: 'Late' } : null
}

export function MyDayPage() {
  const [today, setToday] = useState<Today | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [minute, setMinute] = useState(nowMinutes())

  const load = useCallback(() => {
    jobsApi
      .today()
      .then((t) => {
        setToday(t)
        setError(null)
        // While there's signal, save what today's jobs need for working in a basement
        void saveTodayForOffline(t.jobs)
      })
      .catch((err) => setError(errorMessage(err)))
    setMinute(nowMinutes())
  }, [])

  useEffect(() => {
    load()
    // Refresh when the owner comes back to the app (a phone left open since morning)
    const onVisible = () => document.visibilityState === 'visible' && load()
    document.addEventListener('visibilitychange', onVisible)
    // Something done without signal just reached the server: show the day as it is now
    window.addEventListener('servicelog:synced', load)
    // Keep "Now" / "Next" current while the screen stays open
    const timer = setInterval(() => setMinute(nowMinutes()), 60_000)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener('servicelog:synced', load)
      clearInterval(timer)
    }
  }, [load])

  const [starting, setStarting] = useState<number | null>(null)
  // The stop shown as the big card: the current one, unless another was tapped
  const [openId, setOpenId] = useState<number | null>(null)

  // Warranties ending soon that haven't been offered yet: a reminder, not part of the day's work
  const [warrantiesEnding, setWarrantiesEnding] = useState(0)
  useEffect(() => {
    equipmentApi
      .warrantyEnding()
      .then((list) => setWarrantiesEnding(list.units.filter((u) => !u.offeredOn).length))
      .catch(() => setWarrantiesEnding(0))
  }, [])

  const queue = useSyncQueue()

  // Start a job, or undo an accidental start; then reload the day. Without signal a start
  // is kept on the phone, and undoing a start that hasn't synced just drops it.
  async function changeStatus(job: Job, action: 'start' | 'undoStart') {
    setStarting(job.id)
    const queuedStart = queue.find((q) => q.jobId === job.id && q.kind === 'start-job')
    try {
      if (action === 'undoStart' && queuedStart) {
        await syncQueue.discard(queuedStart.id)
      } else {
        await jobsApi[action](job.id)
        load()
        if (action === 'start') {
          toast.success(`Started · ${job.customerName}`, {
            action: { label: 'Undo', onClick: () => void changeStatus(job, 'undoStart') },
          })
        }
      }
    } catch (err) {
      if (err instanceof OfflineError && action === 'start') {
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
    setStarting(null)
  }

  if (error && !today) {
    return (
      <main>
        <p className="error" role="alert">
          {error}
        </p>
        <button type="button" onClick={load}>
          Try again
        </button>
      </main>
    )
  }
  if (!today) return <Loading />

  // Shown as they'll be once anything done without signal has synced
  const jobs = today.jobs.map((j) => {
    const status = pendingStatus(j.id, queue)
    return status ? { ...j, status, unsynced: true } : { ...j, unsynced: false }
  })
  const highlight = currentOrNext(jobs, minute)
  const openStop = jobs.find((j) => j.id === openId && j.status !== 'DONE') ?? null
  const openIdNow = openStop?.id ?? highlight?.id
  const done = jobs.filter((j) => j.status === 'DONE').length
  const toGo = jobs.length - done - (highlight?.label === 'Now' ? 1 : 0)
  const late = (job: Job) =>
    job.status === 'SCHEDULED' && minutesOfDay(job.startTime) + job.durationMinutes <= minute

  return (
    <main className="my-day">
      <header className="route-header">
        <div>
          <p className="muted no-margin">{formatDate(today.date)}</p>
          <h1>
            {jobs.length === 0 ? 'No stops today' : `${jobs.length} stop${jobs.length === 1 ? '' : 's'} today`}
          </h1>
        </div>
        <Link to="/jobs/new" className="button secondary icon-only" aria-label="Schedule a job">
          <Plus size={22} weight="bold" aria-hidden="true" />
        </Link>
      </header>

      {/* The day at a glance: one segment per stop */}
      {jobs.length > 0 && (
        <div className="day-progress">
          <div className="segments" role="img" aria-label={`${done} of ${jobs.length} stops done`}>
            {jobs.map((j) => (
              <span
                key={j.id}
                className={j.status === 'DONE' ? 'seg done' : highlight?.id === j.id && highlight.label === 'Now' ? 'seg now' : 'seg'}
              />
            ))}
          </div>
          <span className="progress-text">
            {done} done{highlight?.label === 'Now' ? ' · 1 now' : ''} · {toGo} to go
          </span>
        </div>
      )}

      {warrantiesEnding > 0 && (
        <Link to="/warranty" className="banner warning">
          <ShieldWarning size={22} aria-hidden="true" />
          <span>
            {warrantiesEnding} warrant{warrantiesEnding === 1 ? 'y' : 'ies'} ending soon: offer an extension
          </span>
          <CaretRight size={18} aria-hidden="true" />
        </Link>
      )}

      {jobs.length === 0 && (
        <EmptyState
          icon={MapTrifold}
          action={
            <div className="actions">
              <Link to="/jobs/new" className="button">
                Schedule a job
              </Link>
              <Link to="/jobs" className="button secondary">
                See upcoming
              </Link>
            </div>
          }
        >
          Nothing scheduled today.
        </EmptyState>
      )}

      <ol className="route" aria-label="Today's stops">
        {jobs.map((job, i) => {
          const isDone = job.status === 'DONE'
          const isCurrent = openIdNow === job.id
          const isHighlight = highlight?.id === job.id
          const last = i === jobs.length - 1
          const town = townOf(job.address)
          const marker = isDone ? 'done' : isCurrent ? 'current' : 'ahead'
          const line = isDone ? 'solid' : 'dashed'
          return (
            <li key={job.id} className={`stop ${marker}`}>
              <span className="rail" aria-hidden="true">
                <span className="marker">{isDone && <Check size={13} weight="bold" />}</span>
                {!last && <span className={`line ${line}`} />}
              </span>

              {isDone && (
                <Link to={`/jobs/${job.id}`} className="stop-done">
                  <span className="time">{formatTime(job.startTime)} · done</span>
                  <span>
                    {job.customerName} · {town}
                  </span>
                  {job.unsynced && <Pill>Waiting to sync</Pill>}
                </Link>
              )}

              {isCurrent && !isDone && (
                <div className="stop-current">
                  <div className="stop-tags">
                    {isHighlight ? (
                      <Pill tone={highlight?.label === 'Late' ? 'warning' : 'now'}>
                        <span className="time">{formatTime(job.startTime)}</span> · {highlight?.label.toLowerCase()}
                      </Pill>
                    ) : (
                      <Pill tone={late(job) ? 'warning' : undefined}>
                        <span className="time">{formatTime(job.startTime)}</span>
                        {late(job) ? ' · late' : ''}
                      </Pill>
                    )}
                    {job.urgency === 'URGENT' && <Pill tone="urgent">Urgent</Pill>}
                    {job.unsynced && <Pill>Waiting to sync</Pill>}
                  </div>
                  <Link to={`/jobs/${job.id}`} className="stop-name">
                    {job.customerName}
                  </Link>
                  <p className="stop-what">
                    {job.typeLabel} · {formatDuration(job.durationMinutes)}
                  </p>
                  {job.notes && <p className="stop-notes clamp">{job.notes}</p>}
                  <p className="stop-where">{job.address}</p>
                  <div className="stop-actions">
                    <a
                      href={`tel:${job.customerPhone}`}
                      className="button call"
                      aria-label={`Call ${job.customerName} at ${formatPhone(job.customerPhone)}`}
                    >
                      <Phone size={20} aria-hidden="true" />
                      Call
                    </a>
                    <a
                      href={directionsUrl(job.address)}
                      target="_blank"
                      rel="noreferrer"
                      className="button go"
                      aria-label={`Directions to ${job.address}`}
                    >
                      <NavigationArrow size={20} aria-hidden="true" />
                      Drive
                    </a>
                    {/* Start first; once started, the same spot becomes Finish */}
                    {job.status === 'SCHEDULED' ? (
                      <button type="button" onClick={() => changeStatus(job, 'start')} disabled={starting === job.id}>
                        <Play size={20} weight="fill" aria-hidden="true" />
                        {starting === job.id ? 'Starting…' : 'Start'}
                      </button>
                    ) : (
                      <Link to={`/jobs/${job.id}/finish`} className="button">
                        <CheckCircle size={20} weight="fill" aria-hidden="true" />
                        Finish
                      </Link>
                    )}
                  </div>
                  {job.status === 'IN_PROGRESS' && (
                    <button
                      type="button"
                      className="link-button undo-start"
                      onClick={() => changeStatus(job, 'undoStart')}
                      disabled={starting === job.id}
                    >
                      <ArrowCounterClockwise size={18} aria-hidden="true" />
                      Started by mistake? Undo
                    </button>
                  )}
                </div>
              )}

              {!isCurrent && !isDone && (
                <SwipeRow phone={job.customerPhone} directions={directionsUrl(job.address)} label={job.customerName}>
                  <button
                    type="button"
                    className="stop-ahead"
                    onClick={() => setOpenId(job.id)}
                    aria-label={`Open ${job.customerName}, ${formatTime(job.startTime)}`}
                  >
                    <span className="stop-tags">
                      <span className="time">{formatTime(job.startTime)}</span>
                      {job.urgency === 'URGENT' && <Pill tone="urgent">Urgent</Pill>}
                      {late(job) && <Pill tone="warning">Late</Pill>}
                      {job.unsynced && <Pill>Waiting to sync</Pill>}
                    </span>
                    <strong>{job.customerName}</strong>
                    <span className="muted">
                      {job.typeLabel} · {town}
                    </span>
                  </button>
                </SwipeRow>
              )}
            </li>
          )
        })}
      </ol>

      {jobs.length > 0 && done === jobs.length && <p className="route-end muted">Every stop done. Nice work.</p>}
    </main>
  )
}

// "250 State St, Apt 5B, Springfield, MA 01103" -> "Springfield"
function townOf(address: string): string {
  const parts = address.split(', ')
  return parts.length >= 2 ? parts[parts.length - 2] : address
}
