import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { customersApi, type Customer } from '../../api/customers'
import { jobsApi, overlappingJobs, type Job, type JobInput, type JobType, type JobTypeOption } from '../../api/jobs'
import { addDays, formatAddress, formatPhone, formatTime, todayIso } from '../../lib/format'
import { useDebouncedValue } from '../../lib/useDebouncedValue'

// One form for scheduling (/jobs/new, optionally ?customerId=) and rescheduling or
// editing (/jobs/:id/edit). If the time clashes with another job, the owner sees the
// clash and can book anyway.

const DURATIONS = [30, 60, 90, 120, 180, 240, 480]
const LAST_START_HOUR_TODAY = 17

interface Draft {
  propertyId: number | null
  date: string
  startTime: string // HH:MM
  durationMinutes: number
  type: JobType | ''
  urgent: boolean
  notes: string
}

// Next full hour today, or 8 AM tomorrow if it's late in the day
function defaultSlot(): { date: string; startTime: string } {
  const nextHour = new Date().getHours() + 1
  return nextHour <= LAST_START_HOUR_TODAY
    ? { date: todayIso(), startTime: `${String(nextHour).padStart(2, '0')}:00` }
    : { date: addDays(todayIso(), 1), startTime: '08:00' }
}

export function JobFormPage() {
  const params = useParams()
  const editingId = params.id ? Number(params.id) : null
  const presetCustomerId = useSearchParams()[0].get('customerId')
  const navigate = useNavigate()

  const [types, setTypes] = useState<JobTypeOption[]>([])
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [draft, setDraft] = useState<Draft | null>(
    editingId ? null : { propertyId: null, ...defaultSlot(), durationMinutes: 60, type: '', urgent: false, notes: '' },
  )
  const [conflicts, setConflicts] = useState<Job[] | null>(null)
  const [missing, setMissing] = useState<string[]>([])
  const [serverError, setServerError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // Picking a customer when the form wasn't opened from one
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim(), 250)
  const [matches, setMatches] = useState<Customer[]>([])

  useEffect(() => {
    jobsApi
      .types()
      .then(setTypes)
      .catch((err) => setServerError(errorMessage(err)))
  }, [])

  const chooseCustomer = (c: Customer) => {
    setCustomer(c)
    // One address: pick it automatically
    setDraft((d) => d && { ...d, propertyId: c.properties.length === 1 ? c.properties[0].id : null })
  }

  useEffect(() => {
    if (editingId || !presetCustomerId) return
    customersApi
      .get(Number(presetCustomerId))
      .then(chooseCustomer)
      .catch((err) => setServerError(errorMessage(err)))
  }, [editingId, presetCustomerId])

  useEffect(() => {
    if (!editingId) return
    jobsApi
      .get(editingId)
      .then(async (job) => {
        setDraft({
          propertyId: job.propertyId,
          date: job.date,
          startTime: job.startTime.slice(0, 5),
          durationMinutes: job.durationMinutes,
          type: job.type,
          urgent: job.urgency === 'URGENT',
          notes: job.notes ?? '',
        })
        setCustomer(await customersApi.get(job.customerId))
      })
      .catch((err) => setServerError(errorMessage(err)))
  }, [editingId])

  useEffect(() => {
    if (customer || !debouncedQuery) {
      setMatches([])
      return
    }
    const controller = new AbortController()
    customersApi
      .search(debouncedQuery, controller.signal)
      .then(setMatches)
      .catch(() => {})
    return () => controller.abort()
  }, [debouncedQuery, customer])

  if (!draft) {
    return serverError ? (
      <main>
        <p className="error" role="alert">
          {serverError}
        </p>
      </main>
    ) : (
      <p className="loading">Loading…</p>
    )
  }

  const set = <K extends keyof Draft>(field: K, value: Draft[K]) => {
    setDraft({ ...draft, [field]: value })
    setConflicts(null) // any change means the old clash may no longer apply
  }

  async function save(allowOverlap: boolean) {
    if (!draft) return
    const problems = [
      !customer && 'customer',
      !draft.propertyId && 'address',
      !draft.date && 'date',
      !draft.startTime && 'time',
      !draft.type && 'type of job',
    ].filter(Boolean) as string[]
    setMissing(problems)
    if (problems.length > 0) return

    const input: JobInput = {
      propertyId: draft.propertyId!,
      date: draft.date,
      startTime: draft.startTime,
      durationMinutes: draft.durationMinutes,
      type: draft.type as JobType,
      urgency: draft.urgent ? 'URGENT' : 'NORMAL',
      notes: draft.notes.trim() || null,
      allowOverlap,
    }
    setServerError(null)
    setSaving(true)
    try {
      const saved = editingId ? await jobsApi.update(editingId, input) : await jobsApi.create(input)
      navigate(`/jobs/${saved.id}`, { replace: true })
    } catch (err) {
      const clash = overlappingJobs(err)
      if (clash) setConflicts(clash)
      else setServerError(errorMessage(err))
      setSaving(false)
    }
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    save(false)
  }

  const cancelTo = editingId ? `/jobs/${editingId}` : presetCustomerId ? `/customers/${presetCustomerId}` : '/jobs'

  return (
    <main>
      <h1>{editingId ? 'Reschedule or edit job' : 'Schedule a job'}</h1>

      <form onSubmit={handleSubmit} noValidate className="customer-form">
        {/* Customer */}
        {customer ? (
          <div className="picked">
            <span>
              <strong>{customer.name}</strong>
              <br />
              <span className="muted">{formatPhone(customer.phone)}</span>
            </span>
            {!editingId && !presetCustomerId && (
              <button type="button" className="link-button" onClick={() => setCustomer(null)}>
                Change
              </button>
            )}
          </div>
        ) : (
          <>
            <label htmlFor="customer-search">Customer</label>
            <input
              id="customer-search"
              type="search"
              placeholder="Search name, street or phone"
              autoComplete="off"
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <ul className="pick-list">
              {matches.map((c) => (
                <li key={c.id}>
                  <button type="button" className="secondary" onClick={() => chooseCustomer(c)}>
                    {c.name} · {formatPhone(c.phone)}
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}

        {/* Address: chosen automatically when there's only one */}
        {customer && customer.properties.length > 1 && (
          <fieldset className="choice-group">
            <legend>Address</legend>
            <div className="options">
              {customer.properties.map((p) => (
                <label key={p.id} className="option">
                  <input
                    type="radio"
                    name="property"
                    checked={draft.propertyId === p.id}
                    onChange={() => set('propertyId', p.id)}
                  />
                  {formatAddress(p)}
                </label>
              ))}
            </div>
          </fieldset>
        )}
        {customer && customer.properties.length === 1 && (
          <p className="muted">{formatAddress(customer.properties[0])}</p>
        )}

        <fieldset className="choice-group">
          <legend>Type of job</legend>
          <div className="chips">
            {types.map((t) => (
              <label key={t.value} className={draft.type === t.value ? 'chip selected' : 'chip'}>
                <input
                  type="radio"
                  name="type"
                  value={t.value}
                  checked={draft.type === t.value}
                  onChange={() => set('type', t.value)}
                />
                {t.label}
              </label>
            ))}
          </div>
        </fieldset>

        <div className="row even">
          <div>
            <label htmlFor="date">Date</label>
            <input id="date" type="date" value={draft.date} onChange={(e) => set('date', e.target.value)} />
          </div>
          <div>
            <label htmlFor="time">Start time</label>
            <input
              id="time"
              type="time"
              step={900}
              value={draft.startTime}
              onChange={(e) => set('startTime', e.target.value)}
            />
          </div>
        </div>

        <label htmlFor="duration">How long</label>
        <select
          id="duration"
          value={draft.durationMinutes}
          onChange={(e) => set('durationMinutes', Number(e.target.value))}
        >
          {DURATIONS.map((m) => (
            <option key={m} value={m}>
              {m < 60 ? `${m} min` : m === 480 ? 'Full day (8 hr)' : `${m / 60} hr`}
            </option>
          ))}
        </select>

        <label className="checkbox">
          <input type="checkbox" checked={draft.urgent} onChange={(e) => set('urgent', e.target.checked)} />
          Urgent (e.g. no heat, no cooling)
        </label>

        <label htmlFor="notes">
          Notes <span className="optional">(what the customer said, access, parts to bring…)</span>
        </label>
        <textarea id="notes" rows={3} value={draft.notes} onChange={(e) => set('notes', e.target.value)} />

        {missing.length > 0 && (
          <p className="error" role="alert">
            Still needed: {missing.join(', ')}.
          </p>
        )}
        {serverError && (
          <p className="error" role="alert">
            {serverError}
          </p>
        )}

        {conflicts && (
          <div className="overlap" role="alert">
            <strong>This overlaps with:</strong>
            <ul>
              {conflicts.map((c) => (
                <li key={c.id}>
                  {formatTime(c.startTime)} · {c.typeLabel} for {c.customerName}
                </li>
              ))}
            </ul>
            <button type="button" onClick={() => save(true)} disabled={saving}>
              Book anyway
            </button>
          </div>
        )}

        <div className="sticky-actions">
          <Link to={cancelTo} className="button secondary">
            Cancel
          </Link>
          <button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </main>
  )
}
