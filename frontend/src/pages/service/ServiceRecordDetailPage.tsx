import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { serviceRecordsApi, type ServiceRecordDetail } from '../../api/serviceRecords'
import { formatDate } from '../../lib/format'
import { Loading } from '../../components/Loading'

export function ServiceRecordDetailPage() {
  const id = Number(useParams().id)
  const [detail, setDetail] = useState<ServiceRecordDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    serviceRecordsApi
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
  if (!detail) return <Loading />

  const { record: r, equipmentLabel, customerId, customerName, address } = detail

  return (
    <main>
      <div className="page-title">
        <h1>{r.kindLabel}</h1>
        <Link to={`/service-records/${r.id}/edit`} className="button secondary">
          Edit
        </Link>
      </div>
      <p className="muted">
        {formatDate(r.serviceDate)} · <Link to={`/equipment/${r.equipmentId}`}>{equipmentLabel}</Link>
        <br />
        <Link to={`/customers/${customerId}`}>{customerName}</Link> · {address}
      </p>

      {r.followUpNeeded && (
        <p className="follow-up" role="status">
          <strong>Follow-up needed</strong>
          {r.followUpNote && <>: {r.followUpNote}</>}
        </p>
      )}

      <dl className="details">
        <dt>Work done</dt>
        <dd className="notes">{r.workDone}</dd>
        <dt>Parts used</dt>
        <dd className="notes">{r.partsUsed ?? <span className="muted">None recorded</span>}</dd>
        {r.notes && (
          <>
            <dt>Notes</dt>
            <dd className="notes">{r.notes}</dd>
          </>
        )}
      </dl>

      <p className="links">
        <Link to={`/equipment/${r.equipmentId}`}>Back to {equipmentLabel}</Link>
      </p>
    </main>
  )
}
