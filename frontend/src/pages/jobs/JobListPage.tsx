import { CalendarBlank, ListBullets } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { jobsApi, type Job } from '../../api/jobs'
import { addDays, formatDate, todayIso } from '../../lib/format'
import { Loading } from '../../components/Loading'
import { SectionHeading } from '../../components/ui/SectionHeading'
import { JobRows } from './JobRows'
import { MonthView } from './MonthView'

const DAYS_AHEAD = 13 // today plus the next two weeks

// The schedule: the next two weeks as a list, or a month at a glance (?view=month).
// The choice is in the address, so Back from a job returns to the same view.
export function JobListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const month = searchParams.get('view') === 'month'

  function show(view: 'list' | 'month') {
    setSearchParams(view === 'month' ? { view: 'month' } : {}, { replace: true })
  }

  return (
    <main>
      <div className="page-title">
        <h1>Jobs</h1>
        <Link to="/jobs/new" className="button">
          + Schedule
        </Link>
      </div>

      <div className="segmented" role="group" aria-label="Show jobs as">
        <button type="button" aria-pressed={!month} onClick={() => show('list')}>
          <ListBullets size={18} aria-hidden="true" />
          List
        </button>
        <button type="button" aria-pressed={month} onClick={() => show('month')}>
          <CalendarBlank size={18} aria-hidden="true" />
          Month
        </button>
      </div>

      {month ? <MonthView /> : <UpcomingList />}
    </main>
  )
}

function UpcomingList() {
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
    <>
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
          <JobRows jobs={dayJobs} />
        </section>
      ))}
    </>
  )
}
