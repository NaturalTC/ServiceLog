import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '../../api/client'
import { recentlyRemindedAt, tuneUpsApi, type DueList, type DueUnit } from '../../api/tuneUps'
import { daysAgo, formatDate, formatDateTime, formatPhone } from '../../lib/format'

// Customers due for a seasonal tune-up. Units are grouped by address, since one visit
// covers everything there; addresses not yet booked come first.

interface Visit {
  key: string
  units: DueUnit[]
}

function groupByAddress(units: DueUnit[]): Visit[] {
  const visits = new Map<string, Visit>()
  for (const unit of units) {
    const key = `${unit.customerId}-${unit.propertyId}`
    if (!visits.has(key)) visits.set(key, { key, units: [] })
    visits.get(key)!.units.push(unit)
  }
  return [...visits.values()]
}

function seasonText(list: DueList): string {
  if (list.heatingSeason && list.coolingSeason) return 'Heating and cooling season'
  if (list.heatingSeason) return 'Heating season: oil burners, boilers, furnaces, heat pumps and mini-splits'
  if (list.coolingSeason) return 'Cooling season: AC, heat pumps and mini-splits'
  return 'Between seasons'
}

export function TuneUpsPage() {
  const [list, setList] = useState<DueList | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState<string | null>(null)
  const [message, setMessage] = useState<{ key: string; text: string; ok: boolean } | null>(null)

  const load = () =>
    tuneUpsApi
      .due()
      .then(setList)
      .catch((err) => setError(errorMessage(err)))

  useEffect(() => {
    load()
  }, [])

  // Nothing is emailed without the owner's tap and a confirmation
  async function emailReminder(key: string, unit: DueUnit) {
    if (!window.confirm(`Email a tune-up reminder to ${unit.customerName} (${unit.customerEmail})?`)) return
    setSending(key)
    setMessage(null)
    try {
      await send(unit, false)
    } catch (err) {
      const lastSent = recentlyRemindedAt(err)
      if (lastSent && window.confirm(`A reminder already went out ${formatDateTime(lastSent)}. Send another?`)) {
        try {
          await send(unit, true)
        } catch (retryErr) {
          setMessage({ key, text: errorMessage(retryErr), ok: false })
        }
      } else if (!lastSent) {
        setMessage({ key, text: errorMessage(err), ok: false })
      }
    }
    setSending(null)
  }

  async function send(unit: DueUnit, resend: boolean) {
    const sent = await tuneUpsApi.sendReminder(unit.customerId, unit.propertyId, resend)
    const key = `${unit.customerId}-${unit.propertyId}`
    setMessage({ key, text: `Reminder sent to ${sent.recipientEmail}.`, ok: true })
    await load()
  }

  if (error) {
    return (
      <main>
        <p className="error" role="alert">
          {error}
        </p>
      </main>
    )
  }
  if (!list) return <p className="loading">Loading…</p>

  const visits = groupByAddress(list.units)
  const toBook = visits.filter((v) => !v.units[0].bookedJobId)

  return (
    <main>
      <h1>Tune-ups due</h1>
      <p className="muted">
        {seasonText(list)}. Due when the last tune-up was {list.intervalMonths}+ months ago.
      </p>

      {visits.length > 0 && (
        <p>
          <strong>{toBook.length}</strong> address{toBook.length === 1 ? '' : 'es'} to book ·{' '}
          {list.units.length} unit{list.units.length === 1 ? '' : 's'} due
        </p>
      )}
      {visits.length === 0 && (
        <p className="empty">
          {list.heatingSeason || list.coolingSeason
            ? 'Everyone is up to date.'
            : 'No tune-ups are due between seasons.'}
        </p>
      )}

      <ul className="card-list">
        {visits.map(({ key, units }) => {
          const first = units[0]
          const booked = first.bookedJobId !== null
          return (
            <li key={key} className={`card${booked ? ' finished' : ''}`}>
              <span className="card-row">
                <Link to={`/customers/${first.customerId}`}>
                  <strong>{first.customerName}</strong>
                </Link>
                {booked && <span className="badge done">Booked {formatDate(first.bookedDate!)}</span>}
              </span>
              <span className="muted">{first.address}</span>
              {first.lastReminderAt && (
                <span className="muted">Reminder emailed {formatDateTime(first.lastReminderAt)}</span>
              )}

              <ul className="equipment-list">
                {units.map((u) => (
                  <li key={u.equipmentId}>
                    <Link to={`/equipment/${u.equipmentId}`}>
                      <strong>{u.equipmentLabel}</strong>
                      <span className="muted">
                        {[u.brand, u.model].filter(Boolean).join(' ') || 'Brand not recorded'}
                      </span>
                      <span>
                        {u.lastTuneUp
                          ? `Last tune-up ${formatDate(u.lastTuneUp)} (${daysAgo(u.lastTuneUp)})`
                          : 'No tune-up on record'}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>

              {message?.key === key && (
                <p className={message.ok ? 'notice' : 'error'} role="status">
                  {message.text}
                </p>
              )}

              <div className="actions">
                <a href={`tel:${first.customerPhone}`} className="button secondary">
                  Call {formatPhone(first.customerPhone)}
                </a>
                {!booked &&
                  (first.customerEmail ? (
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => emailReminder(key, first)}
                      disabled={sending === key}
                    >
                      {sending === key ? 'Sending…' : 'Email reminder'}
                    </button>
                  ) : (
                    <Link to={`/customers/${first.customerId}/edit`} className="button secondary">
                      Add email
                    </Link>
                  ))}
                {booked ? (
                  <Link to={`/jobs/${first.bookedJobId}`} className="button secondary">
                    View booking
                  </Link>
                ) : (
                  <Link
                    to={`/jobs/new?customerId=${first.customerId}&propertyId=${first.propertyId}&type=TUNE_UP`}
                    className="button"
                  >
                    Schedule tune-up
                  </Link>
                )}
              </div>
            </li>
          )
        })}
      </ul>
    </main>
  )
}
