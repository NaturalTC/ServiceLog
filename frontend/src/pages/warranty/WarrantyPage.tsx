import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '../../api/client'
import { equipmentApi, toInput, type UnregisteredUnit } from '../../api/equipment'
import { daysAgo, formatDate, todayIso } from '../../lib/format'
import { Loading } from '../../components/Loading'

// Installed units whose warranty still has to be registered with the manufacturer,
// newest install first. Units with no install date aren't listed: they may predate the owner.
export function WarrantyPage() {
  const [units, setUnits] = useState<UnregisteredUnit[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState<number | null>(null)

  useEffect(() => {
    equipmentApi
      .unregistered()
      .then(setUnits)
      .catch((err) => setError(errorMessage(err)))
  }, [])

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
    } catch (err) {
      setError(errorMessage(err))
    }
    setSaving(null)
  }

  if (!units) {
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
      <h1>Warranty to register</h1>
      <p className="muted">Installed units not yet registered with the manufacturer.</p>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
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
