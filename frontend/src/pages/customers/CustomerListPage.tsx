import { CaretRight, ShieldWarning } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { customersApi, type Customer } from '../../api/customers'
import { equipmentApi } from '../../api/equipment'
import { formatAddress, formatPhone } from '../../lib/format'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { Loading } from '../../components/Loading'

const SEARCH_DELAY_MS = 250
const MAX_RESULTS = 50 // the server's cap on search results

export function CustomerListPage() {
  // The search lives in the URL (?q=smith), so Back from a customer returns to the same results
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const debouncedQuery = useDebouncedValue(query.trim(), SEARCH_DELAY_MS)

  const [customers, setCustomers] = useState<Customer[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // Cancel the previous request when the query changes, so a slow old response
    // can never replace newer results
    const controller = new AbortController()
    setError(null)
    const request = debouncedQuery
      ? customersApi.search(debouncedQuery, controller.signal)
      : customersApi.list(controller.signal)
    request.then(setCustomers).catch((err) => {
      if (!controller.signal.aborted) setError(errorMessage(err))
    })
    return () => controller.abort()
  }, [debouncedQuery])

  // How many installed units still need their warranty registered (link shown only when some do)
  const [unregistered, setUnregistered] = useState(0)
  useEffect(() => {
    equipmentApi
      .unregistered()
      .then((units) => setUnregistered(units.length))
      .catch(() => setUnregistered(0))
  }, [])

  const setQuery = (value: string) => setSearchParams(value ? { q: value } : {}, { replace: true })
  const searching = debouncedQuery !== ''

  return (
    <main>
      <div className="page-title">
        <h1>Customers</h1>
        <Link to="/customers/new" className="button">
          + New
        </Link>
      </div>

      {unregistered > 0 && (
        <Link to="/warranty#register" className="banner warning">
          <ShieldWarning size={20} aria-hidden="true" />
          <span>
            {unregistered} installed unit{unregistered === 1 ? '' : 's'} not registered for warranty
          </span>
          <CaretRight size={18} aria-hidden="true" />
        </Link>
      )}

      <label htmlFor="customer-search" className="visually-hidden">
        Search customers
      </label>
      <input
        id="customer-search"
        type="search"
        className="search"
        placeholder="Search name, street or phone"
        autoComplete="off"
        enterKeyHint="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {!customers && !error && <Loading />}

      {/* Announced by screen readers as results change */}
      <p className="visually-hidden" aria-live="polite">
        {customers && searching ? `${customers.length} result${customers.length === 1 ? '' : 's'}` : ''}
      </p>

      {customers?.length === 0 &&
        (searching ? (
          <p className="empty">No customers match “{debouncedQuery}”.</p>
        ) : (
          <p className="empty">No customers yet. Tap “+ New” to add your first one.</p>
        ))}

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

      {searching && customers?.length === MAX_RESULTS && (
        <p className="muted center">Showing the first {MAX_RESULTS}. Type more to narrow it down.</p>
      )}
    </main>
  )
}
