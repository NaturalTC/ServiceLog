import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { isOpen, jobsApi, type Job } from '../../api/jobs'
import { formatDate, formatDuration, formatPhone, formatTime, mapsUrl } from '../../lib/format'

export function JobDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

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

  async function startJob() {
    if (!job) return
    setBusy(true)
    try {
      setJob(await jobsApi.start(job.id))
    } catch (err) {
      setError(errorMessage(err))
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
  if (!job) return <p className="loading">Loading…</p>

  const cancelled = job.status === 'CANCELLED'
  const open = isOpen(job)

  return (
    <main>
      <div className="page-title">
        <h1>{job.typeLabel}</h1>
        <span className="tags">
          {job.status === 'IN_PROGRESS' && <span className="badge now">In progress</span>}
          {job.status === 'DONE' && <span className="badge done">Done</span>}
          {job.urgency === 'URGENT' && open && <span className="badge urgent">Urgent</span>}
        </span>
      </div>
      {cancelled && <p className="error">This job was cancelled.</p>}
      {job.status === 'DONE' && (
        <p className="notice" role="status">
          Finished. Service records were saved on the equipment worked on; see the{' '}
          <Link to={`/customers/${job.customerId}`}>customer's equipment</Link>.
        </p>
      )}

      <p>
        <strong>{formatDate(job.date)}</strong>
        <br />
        {formatTime(job.startTime)} · {formatDuration(job.durationMinutes)}
      </p>

      <p>
        <Link to={`/customers/${job.customerId}`}>{job.customerName}</Link>
        <br />
        <span className="muted">{job.address}</span>
      </p>

      <div className="actions">
        <a href={`tel:${job.customerPhone}`} className="button secondary">
          Call {formatPhone(job.customerPhone)}
        </a>
        <a href={mapsUrl(job.address)} target="_blank" rel="noreferrer" className="button secondary">
          Directions
        </a>
      </div>

      {job.notes && <p className="notes">{job.notes}</p>}

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {open && (
        <>
          <div className="actions spaced">
            {job.status === 'SCHEDULED' && (
              <button type="button" className="secondary" onClick={startJob} disabled={busy}>
                Start job
              </button>
            )}
            <Link to={`/jobs/${job.id}/finish`} className="button">
              Finish job
            </Link>
          </div>
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
