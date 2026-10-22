import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '../../api/client'
import { jobsApi, type Job } from '../../api/jobs'
import { addDays, formatDate, formatDuration, formatTime, todayIso } from '../../lib/format'

const DAYS_AHEAD = 13 // today plus the next two weeks

// The upcoming schedule, grouped by day. ("My day", the focused today view, is S8.)
export function JobListPage() {
  const [jobs, setJobs] = useState<Job[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const today = todayIso()
    jobsApi
      .scheduled(today, addDays(today, DAYS_AHEAD))
      .then(setJobs)
      .catch((err) => setError(errorMessage(err)))
  }, [])

  const byDay = new Map<string, Job[]>()
  jobs?.forEach((job) => byDay.set(job.date, [...(byDay.get(job.date) ?? []), job]))

  return (
    <main>
      <div className="page-title">
        <h1>Jobs</h1>
        <Link to="/jobs/new" className="button">
          + Schedule
        </Link>
      </div>
      <p className="muted">Next two weeks</p>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {!jobs && !error && <p className="loading">Loading…</p>}
      {jobs?.length === 0 && <p className="empty">Nothing scheduled in the next two weeks.</p>}

      {[...byDay.entries()].map(([day, dayJobs]) => (
        <section key={day}>
          <h2>{day === todayIso() ? `Today · ${formatDate(day)}` : formatDate(day)}</h2>
          <ul className="card-list">
            {dayJobs.map((job) => (
              <li key={job.id}>
                <Link to={`/jobs/${job.id}`} className="card">
                  <span className="card-row">
                    <strong>
                      {formatTime(job.startTime)} · {job.customerName}
                    </strong>
                    {job.status === 'DONE' && <span className="badge done">Done</span>}
                    {job.status === 'IN_PROGRESS' && <span className="badge now">In progress</span>}
                    {job.urgency === 'URGENT' && job.status !== 'DONE' && <span className="badge urgent">Urgent</span>}
                  </span>
                  <span>
                    {job.typeLabel} · {formatDuration(job.durationMinutes)}
                  </span>
                  <span className="muted">{job.address}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </main>
  )
}
