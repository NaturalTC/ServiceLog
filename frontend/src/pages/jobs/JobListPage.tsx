import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '../../api/client'
import { jobsApi, type Job } from '../../api/jobs'
import { addDays, formatDate, formatDuration, formatTime, todayIso } from '../../lib/format'
import { Loading } from '../../components/Loading'
import { Pill } from '../../components/ui/Pill'
import { Row } from '../../components/ui/Row'
import { SectionHeading } from '../../components/ui/SectionHeading'

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
      {!jobs && !error && <Loading />}
      {jobs?.length === 0 && <p className="empty">Nothing scheduled in the next two weeks.</p>}

      {[...byDay.entries()].map(([day, dayJobs]) => (
        <section key={day}>
          <SectionHeading count={dayJobs.length}>
            {day === todayIso() ? `Today · ${formatDate(day)}` : formatDate(day)}
          </SectionHeading>
          <ul className="rows">
            {dayJobs.map((job) => (
              <li key={job.id}>
                <Row
                  to={`/jobs/${job.id}`}
                  title={
                    <>
                      <span className="time">{formatTime(job.startTime)}</span> · {job.customerName}
                    </>
                  }
                  sub={`${job.typeLabel} · ${formatDuration(job.durationMinutes)} · ${townOf(job.address)}`}
                  end={
                    <>
                      {job.status === 'DONE' && <Pill tone="done">Done</Pill>}
                      {job.status === 'IN_PROGRESS' && <Pill tone="now">In progress</Pill>}
                      {job.urgency === 'URGENT' && job.status !== 'DONE' && <Pill tone="urgent">Urgent</Pill>}
                    </>
                  }
                />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </main>
  )
}

// "250 State St, Apt 5B, Springfield, MA 01103" -> "Springfield"
function townOf(address: string): string {
  const parts = address.split(', ')
  return parts.length >= 2 ? parts[parts.length - 2] : address
}
