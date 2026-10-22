import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { equipmentApi, type EquipmentDetail } from '../../api/equipment'
import { formatInstallDate } from '../../lib/format'

export function EquipmentDetailPage() {
  const id = Number(useParams().id)
  const [detail, setDetail] = useState<EquipmentDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    equipmentApi
      .get(id)
      .then(setDetail)
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
  if (!detail) return <p className="loading">Loading…</p>

  const { equipment: e, customerId, customerName, address } = detail
  const makeModel = [e.brand, e.model].filter(Boolean).join(' ')

  return (
    <main>
      <div className="page-title">
        <h1>{e.typeLabel}</h1>
        <Link to={`/equipment/${e.id}/edit`} className="button secondary">
          Edit
        </Link>
      </div>
      <p className="muted">
        <Link to={`/customers/${customerId}`}>{customerName}</Link> · {address}
      </p>

      <dl className="details">
        <dt>Brand and model</dt>
        <dd>{makeModel || <span className="muted">Not recorded</span>}</dd>
        <dt>Serial number</dt>
        <dd className="mono">{e.serialNumber ?? <span className="muted">Not recorded</span>}</dd>
        <dt>Installed</dt>
        <dd>{e.installDate ? formatInstallDate(e.installDate) : <span className="muted">Unknown</span>}</dd>
        {e.notes && (
          <>
            <dt>Notes</dt>
            <dd className="notes">{e.notes}</dd>
          </>
        )}
      </dl>

      <p className="links">
        <Link to={`/customers/${customerId}`}>Back to {customerName}</Link>
      </p>
    </main>
  )
}
