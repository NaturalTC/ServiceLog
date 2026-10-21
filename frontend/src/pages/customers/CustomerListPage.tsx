import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '../../api/client'
import { customersApi, type Customer } from '../../api/customers'
import { formatAddress, formatPhone } from '../../lib/format'

export function CustomerListPage() {
  const [customers, setCustomers] = useState<Customer[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    customersApi
      .list()
      .then(setCustomers)
      .catch((err) => setError(errorMessage(err)))
  }, [])

  return (
    <main>
      <div className="page-title">
        <h1>Customers</h1>
        <Link to="/customers/new" className="button">
          + New
        </Link>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {!customers && !error && <p className="loading">Loading…</p>}
      {customers?.length === 0 && (
        <p className="empty">No customers yet. Tap “+ New” to add your first one.</p>
      )}

      <ul className="card-list">
        {customers?.map((c) => (
          <li key={c.id}>
            <Link to={`/customers/${c.id}`} className="card">
              <strong>{c.name}</strong>
              <span>{formatPhone(c.phone)}</span>
              {c.properties[0] && <span className="muted">{formatAddress(c.properties[0])}</span>}
              {c.properties.length > 1 && (
                <span className="muted">+{c.properties.length - 1} more address(es)</span>
              )}
            </Link>
          </li>
        ))}
      </ul>
    </main>
  )
}
