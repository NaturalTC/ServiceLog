import { CaretLeft, CaretRight, Plus } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { jobsApi, type Job } from '../../api/jobs'
import { addDays, formatDate, todayIso } from '../../lib/format'
import { SectionHeading } from '../../components/ui/SectionHeading'
import { jobTypeClass } from '../../lib/jobTypes'
import { JobRows } from './JobRows'

// A month at a glance: a calendar with a mark per job, and the tapped day's jobs under it.
// Month and day live in the address (?view=month&day=2026-11-04), so Back keeps your place.

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MAX_MARKS = 4 // more than this and a cell shows "+n"

function parts(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  return { y, m, d }
}

function monthOf(iso: string): string {
  return iso.slice(0, 7) + '-01'
}

function shiftMonth(first: string, by: number): string {
  const { y, m } = parts(first)
  const d = new Date(y, m - 1 + by, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

// Six weeks from the Sunday on or before the 1st: every month fits, and the grid never jumps
function gridDays(first: string): string[] {
  const { y, m } = parts(first)
  const start = addDays(first, -new Date(y, m - 1, 1).getDay())
  return Array.from({ length: 42 }, (_, i) => addDays(start, i))
}

function monthLabel(first: string): string {
  const { y, m } = parts(first)
  return new Date(y, m - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

function longDay(iso: string): string {
  const { y, m, d } = parts(iso)
  return new Date(y, m - 1, d).toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })
}

export function MonthView() {
  const [searchParams, setSearchParams] = useSearchParams()
  const today = todayIso()
  const day = searchParams.get('day') ?? today
  const first = monthOf(day)
  const days = gridDays(first)

  // Months already loaded show at once when you come back to them, then refresh
  const cache = useRef(new Map<string, Job[]>())
  const [jobs, setJobs] = useState<Job[] | null>(cache.current.get(first) ?? null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let current = true
    setJobs(cache.current.get(first) ?? null)
    jobsApi
      .scheduled(days[0], days[days.length - 1])
      .then((list) => {
        cache.current.set(first, list)
        if (current) {
          setJobs(list)
          setError(null)
        }
      })
      .catch((err) => current && setError(errorMessage(err)))
    return () => {
      current = false
    }
    // days follows from first
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [first])

  const byDay = new Map<string, Job[]>()
  jobs?.forEach((job) => byDay.set(job.date, [...(byDay.get(job.date) ?? []), job]))
  const monthJobs = jobs?.filter((j) => j.date.startsWith(first.slice(0, 7))).length ?? 0
  const selected = byDay.get(day) ?? []

  function pick(iso: string) {
    setSearchParams({ view: 'month', day: iso }, { replace: true })
  }

  // Changing month keeps the same day number where it exists (31st -> 30th)
  function goMonth(by: number) {
    const next = shiftMonth(first, by)
    if (monthOf(today) === next) return pick(today)
    const { y, m } = parts(next)
    const last = new Date(y, m, 0).getDate()
    pick(`${next.slice(0, 8)}${String(Math.min(parts(day).d, last)).padStart(2, '0')}`)
  }

  return (
    <section className="month" aria-label="Month">
      <div className="month-header">
        <div>
          <h2>{monthLabel(first)}</h2>
          <p className="muted no-margin">{jobs ? `${monthJobs} job${monthJobs === 1 ? '' : 's'}` : 'Loading…'}</p>
        </div>
        <div className="month-nav">
          {monthOf(today) !== first && (
            <button type="button" className="secondary month-today" onClick={() => pick(today)}>
              Today
            </button>
          )}
          <button type="button" className="month-arrow" onClick={() => goMonth(-1)} aria-label="Previous month">
            <CaretLeft size={20} weight="bold" aria-hidden="true" />
          </button>
          <button type="button" className="month-arrow" onClick={() => goMonth(1)} aria-label="Next month">
            <CaretRight size={20} weight="bold" aria-hidden="true" />
          </button>
        </div>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <div className="month-grid">
        {WEEKDAYS.map((w) => (
          <span key={w} className="month-weekday" aria-hidden="true">
            {w.slice(0, 1)}
          </span>
        ))}
        {days.map((iso) => {
          const list = byDay.get(iso) ?? []
          const inMonth = iso.startsWith(first.slice(0, 7))
          const urgent = list.filter((j) => j.urgency === 'URGENT' && j.status !== 'DONE').length
          const label = `${longDay(iso)}${iso === today ? ', today' : ''}: ${
            list.length === 0 ? 'nothing scheduled' : `${list.length} job${list.length === 1 ? '' : 's'}`
          }${urgent ? `, ${urgent} urgent` : ''}`
          return (
            <button
              key={iso}
              type="button"
              className={`month-day${inMonth ? '' : ' outside'}${iso === today ? ' today' : ''}${iso < today ? ' past' : ''}`}
              aria-pressed={iso === day}
              aria-label={label}
              onClick={() => pick(iso)}
            >
              <span className="month-num">{parts(iso).d}</span>
              <span className="month-marks" aria-hidden="true">
                {list.slice(0, list.length > MAX_MARKS ? MAX_MARKS - 1 : MAX_MARKS).map((j) => (
                  <i
                    key={j.id}
                    className={j.status === 'DONE' ? 'done' : jobTypeClass(j.type)}
                  />
                ))}
                {list.length > MAX_MARKS && <b>+{list.length - (MAX_MARKS - 1)}</b>}
              </span>
            </button>
          )
        })}
      </div>

      <p className="month-legend muted">
        <span><i className="tune-up" /> Tune-up</span>
        <span><i className="service-call" /> Service call</span>
        <span><i className="install" /> Install</span>
        <span><i className="estimate" /> Estimate</span>
        <span><i className="done" /> Done</span>
      </p>

      <section aria-live="polite">
        <SectionHeading count={selected.length}>{day === today ? `Today · ${formatDate(day)}` : formatDate(day)}</SectionHeading>
        {jobs && selected.length === 0 && (
          <p className="empty">Nothing scheduled{day < today ? '' : ' yet'}.</p>
        )}
        {selected.length > 0 && <JobRows jobs={selected} />}
        {day >= today && (
          <Link to={`/jobs/new?date=${day}`} className="button secondary month-add">
            <Plus size={18} weight="bold" aria-hidden="true" />
            Schedule on {longDay(day)}
          </Link>
        )}
      </section>
    </section>
  )
}
