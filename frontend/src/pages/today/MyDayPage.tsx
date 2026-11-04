import {
  ArrowCounterClockwise,
  CaretRight,
  CheckCircle,
  MapPin,
  MapTrifold,
  NavigationArrow,
  Phone,
  Play,
  Plus,
  ShieldWarning,
} from '@phosphor-icons/react'
import { motion } from 'motion/react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { errorMessage, OfflineError } from '../../api/client'
import { customersApi, type Customer } from '../../api/customers'
import { equipmentApi } from '../../api/equipment'
import { atTheHouse, distanceMetres, useHere } from '../../lib/here'
import { jobsApi, type Job, type Today } from '../../api/jobs'
import { directionsUrl, formatDuration, formatPhone, formatTime, minutesOfDay } from '../../lib/format'
import { Loading } from '../../components/Loading'
import { SwipeRow } from '../../components/SwipeRow'
import { EmptyState } from '../../components/ui/EmptyState'
import { JobTypeIcon, JobTypeTag } from '../../components/ui/JobTypeTag'
import { Pill } from '../../components/ui/Pill'
import { saveTodayForOffline } from '../../lib/prefetch'
import { pendingStatus, syncQueue, useSyncQueue } from '../../lib/syncQueue'

// The home screen, iOS style: a large "Today" title, the stop that matters now as a card
// (like a Live Activity) with Call, Directions and Start/Finish, then "Up next" and
// "Completed" as grouped lists. Stops ahead can be tapped into the card or swiped (right
// to call, left to drive).
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

  // Where the phone is (only if the owner turned it on; it never leaves the phone)
  const location = useHere()
  // Every customer's addresses, to say whose house this is when it isn't on today's list
  const [everyone, setEveryone] = useState<Customer[] | null>(null)
  useEffect(() => {
    if (location.here && everyone === null) {
      customersApi
        .list()
        .then(setEveryone)
        .catch(() => setEveryone([]))
    }
  }, [location.here, everyone])

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
  // "I'm here": standing at one of today's addresses brings that stop up, ready to start
  const here = location.here
  const hereJob = here
    ? (jobs
        .filter((j) => j.status !== 'DONE' && atTheHouse(here, j))
        .sort((a, b) => (distanceMetres(here, a) ?? 0) - (distanceMetres(here, b) ?? 0))[0] ?? null)
    : null
  // At a customer's house that isn't on today's list: say whose it is
  const hereCustomer =
    here && !hereJob && everyone
      ? (everyone
          .flatMap((c) => c.properties.map((p) => ({ customer: c, property: p })))
          .filter(({ property }) => atTheHouse(here, { latitude: property.latitude ?? null, longitude: property.longitude ?? null }))
          .map(({ customer }) => customer)[0] ?? null)
      : null
  const cardJob = openStop ?? hereJob ?? jobs.find((j) => j.id === highlight?.id) ?? null
  const ahead = jobs.filter((j) => j.status !== 'DONE' && j.id !== cardJob?.id)
  const finished = jobs.filter((j) => j.status === 'DONE')
  const late = (job: Job) =>
    job.status === 'SCHEDULED' && minutesOfDay(job.startTime) + job.durationMinutes <= minute
  // "In progress", "Now", "Next", "Late", or "Later" for a stop opened by tapping it
  const cardLabel = !cardJob
    ? ''
    : cardJob.status === 'IN_PROGRESS'
      ? 'In progress'
      : cardJob.id === hereJob?.id
        ? 'You’re here'
      : cardJob.id === highlight?.id
        ? highlight.label
        : late(cardJob)
          ? 'Late'
          : 'Later'

  return (
    <main className="my-day">
      <header className="today-header">
        <p className="today-date">{longDate(today.date)}</p>
        <div className="page-title">
          <h1>Today</h1>
          <Link to="/jobs/new" className="round-button" aria-label="Schedule a job">
            <Plus size={20} weight="bold" aria-hidden="true" />
          </Link>
        </div>
        {jobs.length > 0 && (
          <div className="today-progress">
            <span className="bar" role="img" aria-label={`${finished.length} of ${jobs.length} stops done`}>
              <motion.span
                initial={{ width: 0 }}
                animate={{ width: `${(finished.length / jobs.length) * 100}%` }}
                transition={{ duration: 0.7, ease: [0.2, 0.8, 0.2, 1], delay: 0.15 }}
              />
            </span>
            <span>
              {finished.length} of {jobs.length} done
            </span>
          </div>
        )}
      </header>

      {warrantiesEnding > 0 && (
        <Link to="/warranty" className="banner warning">
          <ShieldWarning size={22} aria-hidden="true" />
          <span>
            {warrantiesEnding} warrant{warrantiesEnding === 1 ? 'y' : 'ies'} ending soon: offer an extension
          </span>
          <CaretRight size={18} aria-hidden="true" />
        </Link>
      )}

      {/* Offered once: location makes "I'm here" work. It stays on the phone. */}
      {location.supported && !location.on && !location.decided && jobs.length > 0 && (
        <div className="location-offer">
          <MapPin size={24} weight="fill" aria-hidden="true" />
          <p>
            <strong>Know whose house you’re at</strong>
            Open the app at a job and it’s ready to start. Your location stays on this phone.
          </p>
          <div className="location-offer-actions">
            <button type="button" onClick={location.enable}>
              Turn on location
            </button>
            <button type="button" className="secondary" onClick={location.disable}>
              Not now
            </button>
          </div>
        </div>
      )}

      {hereCustomer && (
        <Link to={`/customers/${hereCustomer.id}`} className="banner here-banner">
          <MapPin size={22} weight="fill" aria-hidden="true" />
          <span>
            You’re at <strong>{hereCustomer.name}</strong>’s
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

      {/* The stop that matters now, like a Live Activity: the next step is one tap away */}
      {cardJob && (
        <motion.article
          key={cardJob.id}
          className="now-card"
          aria-label={`${cardLabel}: ${cardJob.customerName}`}
          initial={{ opacity: 0, scale: 0.97 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ type: 'spring', stiffness: 380, damping: 32 }}
        >
          <p className="now-status">
            <span className={`now-dot ${cardLabel === 'Late' ? 'late' : cardJob.id === hereJob?.id ? 'here' : ''}`}>
              {cardLabel}
            </span>
            <span className="time">
              {formatTime(cardJob.startTime)} – {formatTime(endTime(cardJob))}
            </span>
          </p>
          <Link to={`/jobs/${cardJob.id}`} className="now-name">
            {cardJob.customerName}
          </Link>
          <p className="now-meta">
            <JobTypeTag type={cardJob.type} label={cardJob.typeLabel} /> · {formatDuration(cardJob.durationMinutes)}
            {cardJob.urgency === 'URGENT' && <Pill tone="urgent">Urgent</Pill>}
            {cardJob.unsynced && <Pill>Waiting to sync</Pill>}
          </p>
          {cardJob.notes && <p className="now-notes clamp">{cardJob.notes}</p>}
          <p className="now-where">{cardJob.address}</p>
          <div className="now-actions">
            <a
              href={`tel:${cardJob.customerPhone}`}
              className="button call"
              aria-label={`Call ${cardJob.customerName} at ${formatPhone(cardJob.customerPhone)}`}
            >
              <Phone size={18} weight="fill" aria-hidden="true" />
              Call
            </a>
            <a
              href={directionsUrl(cardJob.address)}
              target="_blank"
              rel="noreferrer"
              className="button go"
              aria-label={`Directions to ${cardJob.address}`}
            >
              <NavigationArrow size={18} weight="fill" aria-hidden="true" />
              Directions
            </a>
          </div>
          {cardJob.status === 'SCHEDULED' ? (
            <button
              type="button"
              className="now-primary"
              onClick={() => changeStatus(cardJob, 'start')}
              disabled={starting === cardJob.id}
            >
              <Play size={18} weight="fill" aria-hidden="true" />
              {starting === cardJob.id ? 'Starting…' : 'Start Job'}
            </button>
          ) : (
            <Link to={`/jobs/${cardJob.id}/finish`} className="button now-primary">
              <CheckCircle size={20} weight="fill" aria-hidden="true" />
              Finish Job
            </Link>
          )}
          {cardJob.status === 'IN_PROGRESS' && (
            <button
              type="button"
              className="link-button undo-start"
              onClick={() => changeStatus(cardJob, 'undoStart')}
              disabled={starting === cardJob.id}
            >
              <ArrowCounterClockwise size={18} aria-hidden="true" />
              Started by mistake? Undo
            </button>
          )}
        </motion.article>
      )}

      {/* The rest of the day: tap one to bring it up in the card; swipe to call or drive */}
      {ahead.length > 0 && (
        <section aria-labelledby="up-next">
          <h2 id="up-next">Up next</h2>
          <ul className="rows">
            {ahead.map((job, i) => (
              <motion.li
                key={job.id}
                layout
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.26, delay: 0.05 + i * 0.04 }}
              >
                <SwipeRow phone={job.customerPhone} directions={directionsUrl(job.address)} label={job.customerName}>
                  <button
                    type="button"
                    className="stop-row"
                    onClick={() => setOpenId(job.id)}
                    aria-label={`Open ${job.customerName}, ${formatTime(job.startTime)}`}
                  >
                    <JobTypeIcon type={job.type} />
                    <span className="stop-row-main">
                      <span className="stop-row-title">{job.customerName}</span>
                      <span className="stop-row-sub">
                        <span className="time">{formatTime(job.startTime)}</span> · {job.typeLabel} · {townOf(job.address)}
                      </span>
                    </span>
                    {job.urgency === 'URGENT' && <Pill tone="urgent">Urgent</Pill>}
                    {late(job) && <Pill tone="warning">Late</Pill>}
                    {job.unsynced && <Pill>Waiting to sync</Pill>}
                    <CaretRight size={16} weight="bold" className="chevron" aria-hidden="true" />
                  </button>
                </SwipeRow>
              </motion.li>
            ))}
          </ul>
        </section>
      )}

      {finished.length > 0 && (
        <section aria-labelledby="completed">
          <h2 id="completed">Completed</h2>
          <ul className="rows">
            {finished.map((job) => (
              <li key={job.id}>
                <Link to={`/jobs/${job.id}`} className="done-row">
                  <motion.span
                    className="done-check"
                    initial={{ scale: 0.4, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    transition={{ type: 'spring', stiffness: 500, damping: 22, delay: 0.1 }}
                  >
                    <CheckCircle size={26} weight="fill" aria-hidden="true" />
                  </motion.span>
                  <span>
                    {job.customerName} · <span className="time">{formatTime(job.startTime)}</span>
                  </span>
                  {job.unsynced && <Pill>Waiting to sync</Pill>}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {jobs.length > 0 && finished.length === jobs.length && (
        <p className="route-end muted">Every stop done. Nice work.</p>
      )}
    </main>
  )
}

// "2026-11-03" -> "Tuesday, November 3"
function longDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })
}

// When the job should be done, as "HH:MM"
function endTime(job: Job): string {
  const end = minutesOfDay(job.startTime) + job.durationMinutes
  return `${String(Math.floor(end / 60) % 24).padStart(2, '0')}:${String(end % 60).padStart(2, '0')}`
}

// "250 State St, Apt 5B, Springfield, MA 01103" -> "Springfield"
function townOf(address: string): string {
  const parts = address.split(', ')
  return parts.length >= 2 ? parts[parts.length - 2] : address
}
