import { Envelope, MapPin, Phone } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { errorMessage } from '../../api/client'
import { customersApi, type Customer } from '../../api/customers'
import { equipmentApi, type Equipment } from '../../api/equipment'
import { jobsApi, type Job } from '../../api/jobs'
import { tuneUpsApi, type Reminder } from '../../api/tuneUps'
import { formatAddress, formatDate, formatDateTime, formatPhone, formatTime, mapsUrl } from '../../lib/format'
import { useConfirm } from '../../components/ConfirmSheet'
import { Loading } from '../../components/Loading'
import { JobTypeTag } from '../../components/ui/JobTypeTag'

export function CustomerDetailPage() {
  const id = Number(useParams().id)
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [equipment, setEquipment] = useState<Equipment[]>([])
  const [jobs, setJobs] = useState<Job[]>([])
  const [reminders, setReminders] = useState<Reminder[]>([])
  const [error, setError] = useState<string | null>(null)
  const [ask, sheet] = useConfirm()

  useEffect(() => {
    Promise.all([
      customersApi.get(id),
      equipmentApi.listForCustomer(id),
      jobsApi.upcomingForCustomer(id),
      tuneUpsApi.history(id),
    ])
      .then(([c, e, j, r]) => {
        setCustomer(c)
        setEquipment(e)
        setJobs(j)
        setReminders(r)
      })
      .catch((err) => setError(errorMessage(err)))
  }, [id])

  async function resumeReminders() {
    if (!customer) return
    const ok = await ask({
      title: 'Turn tune-up reminders back on?',
      message: `Only do this if ${customer.name} asked to get them again.`,
      confirmLabel: 'Turn reminders on',
    })
    if (!ok) return
    try {
      setCustomer(await customersApi.resumeReminders(customer.id))
      toast.success(`Reminders back on for ${customer.name}`)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (error) {
    return (
      <main>
        <p className="error" role="alert">
          {error}
        </p>
        <Link to="/customers">Back to customers</Link>
      </main>
    )
  }
  if (!customer) return <Loading />

  return (
    <main>
      <div className="page-title">
        <h1>{customer.name}</h1>
        <Link to={`/customers/${customer.id}/edit`} className="button secondary">
          Edit
        </Link>
      </div>

      <div className="actions">
        <a href={`tel:${customer.phone}`} className="button call">
          <Phone size={18} aria-hidden="true" />
          Call {formatPhone(customer.phone)}
        </a>
        {customer.email && (
          <a href={`mailto:${customer.email}`} className="button secondary">
            <Envelope size={18} aria-hidden="true" />
            Email
          </a>
        )}
      </div>

      {customer.notes && <p className="notes note-card">{customer.notes}</p>}

      <h2>Upcoming jobs</h2>
      {jobs.length === 0 && <p className="muted">Nothing scheduled.</p>}
      <ul className="card-list">
        {jobs.map((j) => (
          <li key={j.id}>
            <Link to={`/jobs/${j.id}`} className="card">
              <strong>
                {formatDate(j.date)} · {formatTime(j.startTime)}
              </strong>
              <span className="job-type-line">
                <JobTypeTag type={j.type} label={j.typeLabel} />
                {j.urgency === 'URGENT' && <span className="badge urgent">Urgent</span>}
              </span>
            </Link>
          </li>
        ))}
      </ul>
      <div className="actions spaced">
        <Link to={`/jobs/new?customerId=${customer.id}`} className="button">
          + Schedule job
        </Link>
      </div>

      <h2>Properties</h2>
      <ul className="card-list">
        {customer.properties.map((p) => {
          const address = formatAddress(p)
          const units = equipment.filter((e) => e.propertyId === p.id)
          return (
            <li key={p.id} className="card">
              <strong>{address}</strong>
              {p.notes && <span className="muted">{p.notes}</span>}
              <a href={mapsUrl(address)} target="_blank" rel="noreferrer" className="icon-link go">
                <MapPin size={16} aria-hidden="true" />
                Directions
              </a>

              <h3 className="section-label">Equipment</h3>
              {units.length === 0 && <span className="muted">None recorded yet.</span>}
              <ul className="equipment-list">
                {units.map((e) => (
                  <li key={e.id}>
                    <Link to={`/equipment/${e.id}`}>
                      <strong>{e.typeLabel}</strong>
                      <span className="muted">
                        {[e.brand, e.model].filter(Boolean).join(' ') || 'Brand not recorded'}
                        {e.serialNumber && ` · SN ${e.serialNumber}`}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
              <Link to={`/properties/${p.id}/equipment/new?customerId=${customer.id}`} className="button secondary">
                + Add equipment
              </Link>
            </li>
          )
        })}
      </ul>

      {(reminders.length > 0 || customer.remindersOptedOutAt) && (
        <>
          <h2>Reminder emails</h2>
          {customer.remindersOptedOutAt && (
            <div className="notice">
              Unsubscribed from reminder emails on {formatDateTime(customer.remindersOptedOutAt)}.{' '}
              <button type="button" className="link-button" onClick={resumeReminders}>
                Turn back on
              </button>
            </div>
          )}
          <ul className="card-list">
            {reminders.map((r) => (
              <li key={r.id} className="card">
                <span className="card-row">
                  <strong>{formatDateTime(r.sentAt)}</strong>
                  <span className={r.status === 'SENT' ? 'badge done' : 'badge urgent'}>
                    {r.status === 'SENT' ? 'Sent' : 'Failed'}
                  </span>
                </span>
                <span>{r.subject}</span>
                <span className="muted">To {r.recipientEmail}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      <p className="links">
        <Link to="/customers">Back to customers</Link>
      </p>
      {sheet}
    </main>
  )
}
