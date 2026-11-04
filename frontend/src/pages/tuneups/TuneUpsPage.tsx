import { CalendarPlus, Envelope, Phone } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { errorMessage } from '../../api/client'
import { recentlyRemindedAt, tuneUpsApi, type DueList, type DueUnit } from '../../api/tuneUps'
import { daysAgo, formatDate, formatDateTime, formatPhone } from '../../lib/format'
import { useConfirm } from '../../components/ConfirmSheet'
import { Loading } from '../../components/Loading'
import { Panel } from '../../components/ui/Panel'
import { Row } from '../../components/ui/Row'

// Customers due for a seasonal tune-up. Units are grouped by address, since one visit
// covers everything there. "To book" are compact cards (units fold out) with Call, Remind
// and Book in one row; ones already booked are a short list under them.

interface Visit {
  key: string
  units: DueUnit[]
}

// "2 units · last tune-up 21 years ago": the oldest one decides how overdue the visit is
function lastTuneUpText(units: DueUnit[]): string {
  if (units.some((u) => !u.lastTuneUp)) return 'no tune-up on record'
  const oldest = units.map((u) => u.lastTuneUp!).sort()[0]
  return `last tune-up ${daysAgo(oldest)}`
}

// "42 Chestnut St, Apt 3, Springfield, MA 01104" -> "Springfield"
function townOf(address: string): string {
  const parts = address.split(', ')
  return parts.length >= 2 ? parts[parts.length - 2] : address
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


export function TuneUpsPage() {
  const [list, setList] = useState<DueList | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState<string | null>(null)
  const [message, setMessage] = useState<{ key: string; text: string } | null>(null)
  const [ask, sheet] = useConfirm()

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
    const ok = await ask({
      title: `Email a tune-up reminder to ${unit.customerName}?`,
      message: `It goes to ${unit.customerEmail}.`,
      confirmLabel: 'Send reminder',
    })
    if (!ok) return
    setSending(key)
    setMessage(null)
    try {
      await send(unit, false)
    } catch (err) {
      const lastSent = recentlyRemindedAt(err)
      const again =
        lastSent &&
        (await ask({
          title: 'Send another reminder?',
          message: `One already went to ${unit.customerName} ${formatDateTime(lastSent)}.`,
          confirmLabel: 'Send another',
          cancelLabel: "Don't send",
        }))
      if (again) {
        try {
          await send(unit, true)
        } catch (retryErr) {
          setMessage({ key, text: errorMessage(retryErr) })
        }
      } else if (!lastSent) {
        setMessage({ key, text: errorMessage(err) })
      }
    }
    setSending(null)
  }

  async function send(unit: DueUnit, resend: boolean) {
    const sent = await tuneUpsApi.sendReminder(unit.customerId, unit.propertyId, resend)
    toast.success(`Reminder sent to ${sent.recipientEmail}`)
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
  if (!list) return <Loading />

  const visits = groupByAddress(list.units)
  const toBook = visits.filter((v) => !v.units[0].bookedJobId)
  const booked = visits.filter((v) => v.units[0].bookedJobId)

  return (
    <main>
      <h1>Tune-ups due</h1>

      {visits.length === 0 && (
        <p className="empty">
          {list.heatingSeason || list.coolingSeason
            ? 'Everyone is up to date.'
            : 'No tune-ups are due between seasons.'}
        </p>
      )}

      {visits.length > 0 && (
        <Panel title={`To book · ${toBook.length}`}>
          {toBook.length === 0 && <p className="muted no-margin">Everyone due is booked.</p>}
          <ul className="tuneup-list">
            {toBook.map(({ key, units }) => {
              const first = units[0]
              return (
                <li key={key} className="tuneup-card">
                  <div className="tuneup-head">
                    <Link to={`/customers/${first.customerId}`} className="tuneup-name">
                      {first.customerName}
                    </Link>
                    <span className="muted">{townOf(first.address)}</span>
                  </div>
                  <details className="tuneup-units">
                    <summary>
                      {units.length} unit{units.length === 1 ? '' : 's'} · {lastTuneUpText(units)}
                    </summary>
                    <p className="muted no-margin">{first.address}</p>
                    <ul className="rows">
                      {units.map((u) => (
                        <li key={u.equipmentId}>
                          <Row
                            to={`/equipment/${u.equipmentId}`}
                            title={u.equipmentLabel}
                            sub={`${[u.brand, u.model].filter(Boolean).join(' ') || 'Brand not recorded'} · ${
                              u.lastTuneUp ? `last tune-up ${formatDate(u.lastTuneUp)}` : 'no tune-up on record'
                            }`}
                          />
                        </li>
                      ))}
                    </ul>
                  </details>
                  {first.lastReminderAt && (
                    <p className="muted small no-margin">Reminder emailed {formatDateTime(first.lastReminderAt)}</p>
                  )}
                  {message?.key === key && (
                    <p className="error no-margin" role="alert">
                      {message.text}
                    </p>
                  )}
                  <div className="tuneup-actions">
                    <a
                      href={`tel:${first.customerPhone}`}
                      className="button call"
                      aria-label={`Call ${first.customerName}, ${formatPhone(first.customerPhone)}`}
                    >
                      <Phone size={18} aria-hidden="true" />
                      Call
                    </a>
                    {first.remindersOptedOut ? (
                      <span className="tuneup-note muted">Unsubscribed</span>
                    ) : first.customerEmail ? (
                      <button
                        type="button"
                        className="go"
                        onClick={() => emailReminder(key, first)}
                        disabled={sending === key}
                        aria-label={`Email a tune-up reminder to ${first.customerName}`}
                      >
                        <Envelope size={18} aria-hidden="true" />
                        {sending === key ? 'Sending…' : 'Remind'}
                      </button>
                    ) : (
                      <Link to={`/customers/${first.customerId}/edit`} className="button secondary">
                        Add email
                      </Link>
                    )}
                    <Link
                      to={`/jobs/new?customerId=${first.customerId}&propertyId=${first.propertyId}&type=TUNE_UP`}
                      className="button"
                      aria-label={`Book a tune-up for ${first.customerName}`}
                    >
                      <CalendarPlus size={18} aria-hidden="true" />
                      Book
                    </Link>
                  </div>
                </li>
              )
            })}
          </ul>
        </Panel>
      )}

      {booked.length > 0 && (
        <Panel title={`Booked · ${booked.length}`}>
          <ul className="rows">
            {booked.map(({ key, units }) => {
              const first = units[0]
              return (
                <li key={key}>
                  <Row
                    to={`/jobs/${first.bookedJobId}`}
                    title={first.customerName}
                    sub={`${townOf(first.address)} · ${units.length} unit${units.length === 1 ? '' : 's'}`}
                    end={<span className="pill done">{formatDate(first.bookedDate!)}</span>}
                  />
                </li>
              )
            })}
          </ul>
        </Panel>
      )}
      {sheet}
    </main>
  )
}
