import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { errorMessage, OfflineError } from '../../api/client'
import { equipmentApi, type Equipment } from '../../api/equipment'
import { isOpen, jobsApi, type Job, type JobType } from '../../api/jobs'
import { formatTime } from '../../lib/format'
import { Loading } from '../../components/Loading'
import { syncQueue } from '../../lib/syncQueue'

// "Finish job": tick the equipment worked on and describe the work. The app writes one
// service record per unit (kind and date come from the job) and marks the job done.

// A head start on the description; the owner edits it
const WORK_TEMPLATE: Record<JobType, string> = {
  TUNE_UP: 'Tune-up: ',
  SERVICE_CALL: '',
  INSTALL: 'Installed ',
  ESTIMATE_VISIT: '',
}

export function FinishJobPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [units, setUnits] = useState<Equipment[]>([])
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [workDone, setWorkDone] = useState('')
  const [partsUsed, setPartsUsed] = useState('')
  const [notes, setNotes] = useState('')
  const [followUpNeeded, setFollowUpNeeded] = useState(false)
  const [followUpNote, setFollowUpNote] = useState('')
  const [workError, setWorkError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    jobsApi
      .get(id)
      .then(async (j) => {
        setJob(j)
        setWorkDone(WORK_TEMPLATE[j.type])
        const all = await equipmentApi.listForCustomer(j.customerId)
        const here = all.filter((e) => e.propertyId === j.propertyId)
        setUnits(here)
        // One unit, or a tune-up (usually every unit gets serviced): select them up front
        if (here.length === 1 || j.type === 'TUNE_UP') setSelected(new Set(here.map((e) => e.id)))
      })
      .catch((err) => setError(errorMessage(err)))
  }, [id])

  if (error && !job) {
    return (
      <main>
        <p className="error" role="alert">
          {error}
        </p>
      </main>
    )
  }
  if (!job) return <Loading />
  if (!isOpen(job)) {
    return (
      <main>
        <p className="error">This job is {job.status === 'DONE' ? 'already done' : 'cancelled'}.</p>
        <Link to={`/jobs/${job.id}`}>Back to the job</Link>
      </main>
    )
  }

  const toggle = (unitId: number) => {
    const next = new Set(selected)
    if (next.has(unitId)) next.delete(unitId)
    else next.add(unitId)
    setSelected(next)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const description = workDone.trim()
    // The template alone ("Tune-up: ") doesn't count as a description
    const hasWork = description !== '' && description !== WORK_TEMPLATE[job!.type].trim()
    if (selected.size > 0 && !hasWork) {
      setWorkError(true)
      return
    }
    setSaving(true)
    const input = {
      equipmentIds: [...selected],
      workDone: hasWork ? description : null,
      partsUsed: partsUsed.trim() || null,
      notes: notes.trim() || null,
      followUpNeeded,
      followUpNote: followUpNeeded ? followUpNote.trim() || null : null,
    }
    try {
      await jobsApi.complete(job!.id, input)
      toast.success(`Job finished · ${job!.customerName}`)
      navigate('/', { replace: true })
    } catch (err) {
      // No signal: keep it on the phone; the job and its service records sync by itself later
      if (err instanceof OfflineError) {
        await syncQueue.add({
          kind: 'finish-job',
          label: `Finish job · ${job!.customerName}`,
          method: 'POST',
          path: `/api/jobs/${job!.id}/complete`,
          body: input,
          jobId: job!.id,
        })
        // Today shows it as done, marked "Waiting to sync"
        toast.success('Finished without signal. It will sync by itself.')
        navigate('/', { replace: true })
        return
      }
      setError(errorMessage(err))
      setSaving(false)
    }
  }

  return (
    <main>
      <h1>Finish job</h1>
      <p className="muted">
        {job.typeLabel} · {formatTime(job.startTime)} · {job.customerName}
        <br />
        {job.address}
      </p>
      {job.notes && <p className="notes">{job.notes}</p>}

      <form onSubmit={handleSubmit} noValidate className="customer-form">
        <fieldset className="choice-group">
          <legend>Equipment worked on</legend>
          {units.length === 0 ? (
            <p className="muted">
              No equipment recorded at this address.{' '}
              <Link to={`/properties/${job.propertyId}/equipment/new?customerId=${job.customerId}`}>Add equipment</Link>{' '}
              first, or finish without a service record.
            </p>
          ) : (
            <div className="options">
              {units.map((u) => (
                <label key={u.id} className="option">
                  <input type="checkbox" checked={selected.has(u.id)} onChange={() => toggle(u.id)} />
                  <span>
                    <strong>{u.typeLabel}</strong>
                    <br />
                    <span className="muted">
                      {[u.brand, u.model].filter(Boolean).join(' ') || 'Brand not recorded'}
                      {u.serialNumber && ` · SN ${u.serialNumber}`}
                    </span>
                  </span>
                </label>
              ))}
            </div>
          )}
        </fieldset>

        {selected.size > 0 && (
          <>
            <label htmlFor="work-done">
              Work done <span className="optional">(saved on each selected unit)</span>
            </label>
            <textarea
              id="work-done"
              rows={4}
              value={workDone}
              onChange={(e) => {
                setWorkDone(e.target.value)
                setWorkError(false)
              }}
              aria-invalid={workError}
              aria-describedby={workError ? 'work-error' : undefined}
            />
            {workError && (
              <p className="error field-error" id="work-error">
                Describe the work done.
              </p>
            )}

            <label htmlFor="parts">
              Parts used <span className="optional">(optional)</span>
            </label>
            <textarea id="parts" rows={2} value={partsUsed} onChange={(e) => setPartsUsed(e.target.value)} />

            <label htmlFor="notes">
              Notes <span className="optional">(optional)</span>
            </label>
            <textarea id="notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />

            <label className="checkbox">
              <input type="checkbox" checked={followUpNeeded} onChange={(e) => setFollowUpNeeded(e.target.checked)} />
              Follow-up needed
            </label>
            {followUpNeeded && (
              <input
                aria-label="What's needed"
                placeholder="What's needed?"
                value={followUpNote}
                onChange={(e) => setFollowUpNote(e.target.value)}
              />
            )}
          </>
        )}

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <div className="sticky-actions">
          <Link to={`/jobs/${job.id}`} className="button secondary">
            Cancel
          </Link>
          <button type="submit" disabled={saving}>
            {saving ? 'Saving…' : selected.size > 0 ? `Finish · ${selected.size} record${selected.size === 1 ? '' : 's'}` : 'Finish job'}
          </button>
        </div>
      </form>
    </main>
  )
}
