import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { jobsApi, type Job } from '../../api/jobs'
import { formatDate, formatDuration, formatPhone, formatTime, mapsUrl } from '../../lib/format'

export function JobDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState(false)

  useEffect(() => {
    jobsApi
      .get(id)
      .then(setJob)
      .catch((err) => setError(errorMessage(err)))
  }, [id])

  async function cancelJob() {
    if (!job || !window.confirm(`Cancel the ${job.typeLabel.toLowerCase()} for ${job.customerName}?`)) return
    setCancelling(true)
    try {
      await jobsApi.cancel(job.id)
      navigate('/jobs', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      setCancelling(false)
    }
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

  return (
    <main>
      <div className="page-title">
        <h1>{job.typeLabel}</h1>
        {job.urgency === 'URGENT' && <span className="badge urgent">Urgent</span>}
      </div>
      {cancelled && <p className="error">This job was cancelled.</p>}

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

      {!cancelled && (
        <div className="actions spaced">
          <Link to={`/jobs/${job.id}/edit`} className="button">
            Reschedule or edit
          </Link>
          <button type="button" className="secondary danger" onClick={cancelJob} disabled={cancelling}>
            {cancelling ? 'Cancelling…' : 'Cancel job'}
          </button>
        </div>
      )}

      <p className="links">
        <Link to="/jobs">Back to jobs</Link>
      </p>
    </main>
  )
}
