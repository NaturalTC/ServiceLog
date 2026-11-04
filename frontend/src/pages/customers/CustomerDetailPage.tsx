import { ClockCounterClockwise, Envelope, MapPin, Phone, Toolbox, UserCircle } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { errorMessage } from '../../api/client'
import { customersApi, type Customer } from '../../api/customers'
import { equipmentApi, type Equipment } from '../../api/equipment'
import { jobsApi, type Job } from '../../api/jobs'
import { serviceRecordsApi, type ServiceRecord } from '../../api/serviceRecords'
import { tuneUpsApi, type Reminder } from '../../api/tuneUps'
import { daysAgo, formatAddress, formatDate, formatDateTime, formatPhone, formatTime, mapsUrl } from '../../lib/format'
import { useConfirm } from '../../components/ConfirmSheet'
import { Loading } from '../../components/Loading'
import { JobTypeTag } from '../../components/ui/JobTypeTag'
import { Panel } from '../../components/ui/Panel'
import { Row } from '../../components/ui/Row'

// A customer in three tabs, so you only see what you came for:
// Overview (notes, upcoming jobs, addresses), Equipment (by address), History (work done
// and reminder emails). Call and Email stay above the tabs. The tab is in the address
// (?tab=equipment), so Back from a unit returns to the same tab.
type Tab = 'overview' | 'equipment' | 'history'
const TABS: { id: Tab; label: string; icon: typeof UserCircle }[] = [
  { id: 'overview', label: 'Overview', icon: UserCircle },
  { id: 'equipment', label: 'Equipment', icon: Toolbox },
  { id: 'history', label: 'History', icon: ClockCounterClockwise },
]

interface WorkDone {
  record: ServiceRecord
  unit: Equipment
}

export function CustomerDetailPage() {
  const id = Number(useParams().id)
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = (TABS.find((t) => t.id === searchParams.get('tab'))?.id ?? 'overview') as Tab
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [equipment, setEquipment] = useState<Equipment[]>([])
  const [jobs, setJobs] = useState<Job[]>([])
  const [reminders, setReminders] = useState<Reminder[]>([])
  const [history, setHistory] = useState<WorkDone[] | null>(null)
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

  // Work done on every unit, newest first: loaded when History is first opened
  useEffect(() => {
    if (tab !== 'history' || history !== null || !customer) return
    Promise.all(equipment.map((unit) => serviceRecordsApi.history(unit.id).then((rs) => rs.map((record) => ({ record, unit })))))
      .then((lists) =>
        setHistory(lists.flat().sort((a, b) => b.record.serviceDate.localeCompare(a.record.serviceDate))),
      )
      .catch((err) => setError(errorMessage(err)))
  }, [tab, history, customer, equipment])

  function show(next: Tab) {
    setSearchParams(next === 'overview' ? {} : { tab: next }, { replace: true })
  }

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

  if (error && !customer) {
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

      <div className="segmented three" role="group" aria-label="Show">
        {TABS.map((t) => (
          <button key={t.id} type="button" aria-pressed={tab === t.id} onClick={() => show(t.id)}>
            <t.icon size={18} aria-hidden="true" />
            {t.label}
          </button>
        ))}
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {tab === 'overview' && (
        <>
          {customer.notes && (
            <Panel title="Notes">
              <p className="notes note-card">{customer.notes}</p>
            </Panel>
          )}

          <Panel title={`Upcoming jobs${jobs.length ? ` · ${jobs.length}` : ''}`}>
            {jobs.length === 0 ? (
              <p className="muted no-margin">Nothing scheduled.</p>
            ) : (
              <ul className="rows">
                {jobs.map((j) => (
                  <li key={j.id}>
                    <Row
                      to={`/jobs/${j.id}`}
                      title={
                        <>
                          {formatDate(j.date)} · <span className="time">{formatTime(j.startTime)}</span>
                        </>
                      }
                      sub={<JobTypeTag type={j.type} label={j.typeLabel} />}
                      end={j.urgency === 'URGENT' && <span className="pill urgent">Urgent</span>}
                    />
                  </li>
                ))}
              </ul>
            )}
            <Link to={`/jobs/new?customerId=${customer.id}`} className="button">
              + Schedule job
            </Link>
          </Panel>

          <Panel title={customer.properties.length > 1 ? 'Addresses' : 'Address'}>
            <ul className="rows">
              {customer.properties.map((p) => {
                const address = formatAddress(p)
                return (
                  <li key={p.id} className="address-row">
                    <span>
                      <strong>{address}</strong>
                      {p.notes && <span className="muted block">{p.notes}</span>}
                    </span>
                    <a href={mapsUrl(address)} target="_blank" rel="noreferrer" className="icon-link go">
                      <MapPin size={16} aria-hidden="true" />
                      Directions
                    </a>
                  </li>
                )
              })}
            </ul>
          </Panel>
        </>
      )}

      {tab === 'equipment' &&
        customer.properties.map((p) => {
          const units = equipment.filter((e) => e.propertyId === p.id)
          return (
            <Panel key={p.id} title={formatAddress(p)}>
              {units.length === 0 ? (
                <p className="muted no-margin">No equipment recorded at this address yet.</p>
              ) : (
                <ul className="rows">
                  {units.map((e) => (
                    <li key={e.id}>
                      <Row
                        to={`/equipment/${e.id}`}
                        title={e.typeLabel}
                        sub={
                          <>
                            {[e.brand, e.model].filter(Boolean).join(' ') || 'Brand not recorded'}
                            {e.serialNumber && <span className="mono"> · SN {e.serialNumber}</span>}
                          </>
                        }
                      />
                    </li>
                  ))}
                </ul>
              )}
              <Link to={`/properties/${p.id}/equipment/new?customerId=${customer.id}`} className="button secondary">
                + Add equipment
              </Link>
            </Panel>
          )
        })}

      {tab === 'history' && (
        <>
          <Panel title="Work done">
            {history === null && <Loading cards={2} />}
            {history?.length === 0 && <p className="muted no-margin">No service records yet.</p>}
            {history && history.length > 0 && (
              <ul className="rows">
                {history.map(({ record, unit }) => (
                  <li key={record.id}>
                    <Row
                      to={`/service-records/${record.id}`}
                      title={`${record.kindLabel} · ${unit.typeLabel}`}
                      sub={`${formatDate(record.serviceDate)} (${daysAgo(record.serviceDate)}) · ${record.workDone}`}
                    />
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="Reminder emails">
            {customer.remindersOptedOutAt && (
              <div className="notice">
                Unsubscribed from reminder emails on {formatDateTime(customer.remindersOptedOutAt)}.{' '}
                <button type="button" className="link-button" onClick={resumeReminders}>
                  Turn back on
                </button>
              </div>
            )}
            {reminders.length === 0 && !customer.remindersOptedOutAt && (
              <p className="muted no-margin">No reminder emails sent yet.</p>
            )}
            {reminders.length > 0 && (
              <ul className="rows">
                {reminders.map((r) => (
                  <li key={r.id}>
                    <Row
                      title={formatDateTime(r.sentAt)}
                      sub={`${r.subject} · to ${r.recipientEmail}`}
                      end={
                        <span className={r.status === 'SENT' ? 'pill done' : 'pill urgent'}>
                          {r.status === 'SENT' ? 'Sent' : 'Failed'}
                        </span>
                      }
                    />
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </>
      )}

      <p className="links">
        <Link to="/customers">Back to customers</Link>
      </p>
      {sheet}
    </main>
  )
}
