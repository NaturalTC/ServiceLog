import { Envelope, Phone } from '@phosphor-icons/react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { toast } from 'sonner'
import { errorMessage } from '../../api/client'
import { equipmentApi, toInput, type ExpiringWarranties, type UnregisteredUnit } from '../../api/equipment'
import { Loading } from '../../components/Loading'
import { daysAgo, formatDate, formatPhone, todayIso } from '../../lib/format'

// Warranty work in one place: cover ending soon (offer an extended warranty or a
// renewal, S25), and installed units still to register with the manufacturer (S24).

function endsIn(days: number): string {
  if (days === 0) return 'today'
  if (days === 1) return 'tomorrow'
  return `in ${days} days`
}

export function WarrantyPage() {
  const [ending, setEnding] = useState<ExpiringWarranties | null>(null)
  const [units, setUnits] = useState<UnregisteredUnit[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState<number | null>(null)

  const load = useCallback(() => {
    Promise.all([equipmentApi.warrantyEnding(), equipmentApi.unregistered()])
      .then(([e, u]) => {
        setEnding(e)
        setUnits(u)
      })
      .catch((err) => setError(errorMessage(err)))
  }, [])

  useEffect(load, [load])

  // The Customers banner links to #register: scroll there once the lists are in
  const { hash } = useLocation()
  const loaded = ending !== null && units !== null
  useEffect(() => {
    if (loaded && hash) document.getElementById(hash.slice(1))?.scrollIntoView()
  }, [loaded, hash])

  async function markOffered(id: number, offered: boolean, customerName?: string) {
    setSaving(id)
    setError(null)
    try {
      await equipmentApi.markOffered(id, offered)
      setEnding(await equipmentApi.warrantyEnding())
      if (offered) {
        toast.success(`Marked offered${customerName ? ` for ${customerName}` : ''}`, {
          action: { label: 'Undo', onClick: () => void markOffered(id, false) },
        })
      }
    } catch (err) {
      setError(errorMessage(err))
    }
    setSaving(null)
  }

  // One tap once the registration is submitted; the date can be changed on the unit's Edit page
  async function markRegistered(id: number) {
    setSaving(id)
    setError(null)
    try {
      const { equipment } = await equipmentApi.get(id)
      await equipmentApi.update(id, {
        ...toInput(equipment),
        warrantyRegistered: true,
        warrantyRegisteredOn: todayIso(),
      })
      setUnits((current) => current?.filter((u) => u.equipmentId !== id) ?? null)
      toast.success(`Registered: ${equipment.typeLabel}`)
    } catch (err) {
      setError(errorMessage(err))
    }
    setSaving(null)
  }

  if (!ending || !units) {
    return error ? (
      <main>
        <p className="error" role="alert">
          {error}
        </p>
      </main>
    ) : (
      <Loading />
    )
  }

  return (
    <main>
      <h1>Warranty</h1>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <h2>Ending in the next {ending.windowDays} days</h2>
      {ending.units.length === 0 && <p className="empty">No warranties ending soon.</p>}
      <ul className="card-list">
        {ending.units.map((w) => {
          const offered = w.offeredOn !== null
          const offer = w.offer === 'RENEWAL' ? 'Offer a renewal' : 'Offer an extended warranty'
          return (
            <li key={w.equipmentId} className={`card${offered ? ' finished' : ''}`}>
              <span className="card-row">
                <Link to={`/equipment/${w.equipmentId}`} className="card-title">
                  {w.typeLabel}
                </Link>
                {offered ? (
                  <span className="badge done">Offered {formatDate(w.offeredOn!)}</span>
                ) : (
                  <span className={`badge${w.daysLeft <= 14 ? ' urgent' : ' now'}`}>Ends {endsIn(w.daysLeft)}</span>
                )}
              </span>
              {(w.brand || w.model) && <span className="muted">{[w.brand, w.model].filter(Boolean).join(' ')}</span>}
              <span>
                {w.offer === 'RENEWAL' ? 'Extended warranty' : 'Warranty'} ends {formatDate(w.endsOn)}
              </span>
              <span className="follow-up-tag">{offer}</span>
              <Link to={`/customers/${w.customerId}`}>{w.customerName}</Link>
              <span className="muted">{w.address}</span>
              <div className="actions">
                <a href={`tel:${w.customerPhone}`} className="button call">
                  <Phone size={18} aria-hidden="true" />
                  Call
                </a>
                {w.customerEmail && (
                  <a href={`mailto:${w.customerEmail}`} className="button secondary">
                    <Envelope size={18} aria-hidden="true" />
                    Email
                  </a>
                )}
              </div>
              {offered ? (
                <button
                  type="button"
                  className="link-button center-self"
                  onClick={() => markOffered(w.equipmentId, false)}
                  disabled={saving === w.equipmentId}
                >
                  Undo offered
                </button>
              ) : (
                <button
                  type="button"
                  className="dark"
                  onClick={() => markOffered(w.equipmentId, true, w.customerName)}
                  disabled={saving === w.equipmentId}
                  aria-label={`Mark offered: ${w.typeLabel} for ${w.customerName}, ${formatPhone(w.customerPhone)}`}
                >
                  {saving === w.equipmentId ? 'Saving…' : 'Mark offered'}
                </button>
              )}
            </li>
          )
        })}
      </ul>

      <h2 id="register">To register</h2>
      <p className="muted">Installed units not yet registered with the manufacturer.</p>
      {units.length === 0 && <p className="empty">Every installed unit is registered.</p>}
      <ul className="card-list">
        {units.map((u) => (
          <li key={u.equipmentId} className="card">
            <Link to={`/equipment/${u.equipmentId}`} className="card-title">
              {u.typeLabel}
            </Link>
            {(u.brand || u.model) && <span className="muted">{[u.brand, u.model].filter(Boolean).join(' ')}</span>}
            <span>
              Installed {formatDate(u.installDate)} ({daysAgo(u.installDate)})
            </span>
            {u.serialNumber ? (
              <span className="mono">Serial {u.serialNumber}</span>
            ) : (
              <span className="follow-up-tag">Serial number needed to register</span>
            )}
            <Link to={`/customers/${u.customerId}`}>{u.customerName}</Link>
            <span className="muted">{u.address}</span>
            <div className="actions">
              <Link to={`/equipment/${u.equipmentId}/edit`} className="button secondary">
                {u.serialNumber ? 'Edit' : 'Add serial'}
              </Link>
              <button type="button" onClick={() => markRegistered(u.equipmentId)} disabled={saving === u.equipmentId}>
                {saving === u.equipmentId ? 'Saving…' : 'Registered today'}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </main>
  )
}
