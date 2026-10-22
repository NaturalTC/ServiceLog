import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '../../api/client'
import { jobsApi, type Job, type Today } from '../../api/jobs'
import {
  directionsUrl,
  formatDate,
  formatDuration,
  formatPhone,
  formatTime,
  minutesOfDay,
} from '../../lib/format'

// The home screen: today's jobs in time order, each with one-tap Call and Navigate.
// "Today" comes from the server (the business's time zone), not the phone's clock.

function nowMinutes(): number {
  const now = new Date()
  return now.getHours() * 60 + now.getMinutes()
}

// The job happening now, or else the next one still to come (finished jobs don't count)
function currentOrNext(jobs: Job[], minute: number): { id: number; label: 'Now' | 'Next' } | null {
  jobs = jobs.filter((j) => j.status !== 'DONE')
  const inProgress = jobs.find((j) => j.status === 'IN_PROGRESS')
  if (inProgress) return { id: inProgress.id, label: 'Now' }
  const now = jobs.find((j) => {
    const start = minutesOfDay(j.startTime)
    return start <= minute && minute < start + j.durationMinutes
  })
  if (now) return { id: now.id, label: 'Now' }
  const next = jobs.find((j) => minutesOfDay(j.startTime) > minute)
  return next ? { id: next.id, label: 'Next' } : null
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
      })
      .catch((err) => setError(errorMessage(err)))
    setMinute(nowMinutes())
  }, [])

  useEffect(() => {
    load()
    // Refresh when the owner comes back to the app (a phone left open since morning)
    const onVisible = () => document.visibilityState === 'visible' && load()
    document.addEventListener('visibilitychange', onVisible)
    // Keep "Now" / "Next" current while the screen stays open
    const timer = setInterval(() => setMinute(nowMinutes()), 60_000)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      clearInterval(timer)
    }
  }, [load])

  const [starting, setStarting] = useState<number | null>(null)

  async function startJob(jobId: number) {
    setStarting(jobId)
    try {
      await jobsApi.start(jobId)
      load()
    } catch (err) {
      setError(errorMessage(err))
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
  if (!today) return <p className="loading">Loading…</p>

  const { jobs } = today
  const highlight = currentOrNext(jobs, minute)
  const remaining = jobs.filter((j) => j.status !== 'DONE').length

  return (
    <main>
      <div className="page-title">
        <div>
          <h1>My day</h1>
          <p className="muted no-margin">{formatDate(today.date)}</p>
        </div>
        <Link to="/jobs/new" className="button secondary">
          + Job
        </Link>
      </div>

      {jobs.length > 0 && (
        <p className="muted">
          {jobs.length} job{jobs.length === 1 ? '' : 's'} today
          {remaining < jobs.length && ` · ${remaining} left`}
        </p>
      )}

      {jobs.length === 0 && (
        <div className="empty-day">
          <p>Nothing scheduled today.</p>
          <div className="actions">
            <Link to="/jobs/new" className="button">
              Schedule a job
            </Link>
            <Link to="/jobs" className="button secondary">
              See upcoming
            </Link>
          </div>
        </div>
      )}

      <ol className="day-list">
        {jobs.map((job) => {
          const start = minutesOfDay(job.startTime)
          const done = job.status === 'DONE'
          const finished = done || (start + job.durationMinutes <= minute && job.status !== 'IN_PROGRESS')
          const tag = highlight?.id === job.id ? highlight.label : null
          return (
            <li key={job.id} className={`day-job${tag ? ' highlighted' : ''}${finished ? ' finished' : ''}`}>
              <Link to={`/jobs/${job.id}`} className="day-job-main">
                <span className="card-row">
                  <strong className="time">
                    {formatTime(job.startTime)}
                    <span className="muted"> · {formatDuration(job.durationMinutes)}</span>
                  </strong>
                  <span className="tags">
                    {done && <span className="badge done">Done</span>}
                    {tag && <span className="badge now">{tag}</span>}
                    {job.urgency === 'URGENT' && !done && <span className="badge urgent">Urgent</span>}
                  </span>
                </span>
                <strong className="customer">{job.customerName}</strong>
                <span>{job.typeLabel}</span>
                <span className="muted">{job.address}</span>
                {job.notes && <span className="clamp muted">{job.notes}</span>}
              </Link>
              {!done && (
                <div className="day-job-actions">
                  <a
                    href={`tel:${job.customerPhone}`}
                    className="button secondary"
                    aria-label={`Call ${job.customerName}`}
                  >
                    Call {formatPhone(job.customerPhone)}
                  </a>
                  <a
                    href={directionsUrl(job.address)}
                    target="_blank"
                    rel="noreferrer"
                    className="button"
                    aria-label={`Navigate to ${job.address}`}
                  >
                    Navigate
                  </a>
                  {/* Start first; once started, the same spot becomes Finish */}
                  {job.status === 'SCHEDULED' ? (
                    <button
                      type="button"
                      className="secondary span-2"
                      onClick={() => startJob(job.id)}
                      disabled={starting === job.id}
                    >
                      {starting === job.id ? 'Starting…' : 'Start job'}
                    </button>
                  ) : (
                    <Link to={`/jobs/${job.id}/finish`} className="button span-2">
                      Finish job
                    </Link>
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ol>
    </main>
  )
}
