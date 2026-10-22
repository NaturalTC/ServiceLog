import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { customersApi, type Customer } from '../../api/customers'
import { equipmentApi, type Equipment } from '../../api/equipment'
import { jobsApi, type Job } from '../../api/jobs'
import { formatAddress, formatDate, formatPhone, formatTime, mapsUrl } from '../../lib/format'

export function CustomerDetailPage() {
  const id = Number(useParams().id)
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [equipment, setEquipment] = useState<Equipment[]>([])
  const [jobs, setJobs] = useState<Job[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([customersApi.get(id), equipmentApi.listForCustomer(id), jobsApi.upcomingForCustomer(id)])
      .then(([c, e, j]) => {
        setCustomer(c)
        setEquipment(e)
        setJobs(j)
      })
      .catch((err) => setError(errorMessage(err)))
  }, [id])

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
  if (!customer) return <p className="loading">Loading…</p>

  return (
    <main>
      <div className="page-title">
        <h1>{customer.name}</h1>
        <Link to={`/customers/${customer.id}/edit`} className="button secondary">
          Edit
        </Link>
      </div>

      <div className="actions">
        <a href={`tel:${customer.phone}`} className="button">
          Call {formatPhone(customer.phone)}
        </a>
        {customer.email && (
          <a href={`mailto:${customer.email}`} className="button secondary">
            Email
          </a>
        )}
      </div>

      {customer.notes && <p className="notes">{customer.notes}</p>}

      <h2>Upcoming jobs</h2>
      {jobs.length === 0 && <p className="muted">Nothing scheduled.</p>}
      <ul className="card-list">
        {jobs.map((j) => (
          <li key={j.id}>
            <Link to={`/jobs/${j.id}`} className="card">
              <strong>
                {formatDate(j.date)} · {formatTime(j.startTime)}
              </strong>
              <span>
                {j.typeLabel}
                {j.urgency === 'URGENT' && ' · Urgent'}
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
              <span>{address}</span>
              {p.notes && <span className="muted">{p.notes}</span>}
              <a href={mapsUrl(address)} target="_blank" rel="noreferrer">
                Directions
              </a>

              <h3>Equipment</h3>
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

      <p className="links">
        <Link to="/customers">Back to customers</Link>
      </p>
    </main>
  )
}
