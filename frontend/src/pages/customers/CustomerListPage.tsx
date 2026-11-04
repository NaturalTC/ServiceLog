import { CaretRight, MapPin, Plus, ShieldWarning } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { errorMessage } from '../../api/client'
import { customersApi, type Customer } from '../../api/customers'
import { equipmentApi } from '../../api/equipment'
import { formatPhone } from '../../lib/format'
import { atTheHouse, distanceMetres, formatDistance, locateOnce, supported } from '../../lib/here'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { Loading } from '../../components/Loading'
import { Row } from '../../components/ui/Row'

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

  // "Who's this house?": the customer whose address is where the phone is
  const navigate = useNavigate()
  const [finding, setFinding] = useState(false)
  async function whoIsHere() {
    setFinding(true)
    try {
      const [here, everyone] = await Promise.all([locateOnce(), customersApi.list()])
      const nearest = everyone
        .flatMap((c) =>
          c.properties.map((p) => ({
            customer: c,
            place: { latitude: p.latitude ?? null, longitude: p.longitude ?? null },
          })),
        )
        .map((x) => ({ ...x, metres: distanceMetres(here, x.place) }))
        .filter((x): x is typeof x & { metres: number } => x.metres != null)
        .sort((a, b) => a.metres - b.metres)[0]
      if (nearest && atTheHouse(here, nearest.place)) {
        toast.success(`You’re at ${nearest.customer.name}’s`)
        navigate(`/customers/${nearest.customer.id}`)
      } else if (nearest) {
        toast(`No customer at this spot. Closest: ${nearest.customer.name}, ${formatDistance(nearest.metres)} away`, {
          action: { label: 'Open', onClick: () => navigate(`/customers/${nearest.customer.id}`) },
        })
      } else {
        toast('No customer addresses are on the map yet. They’re looked up in the background; try again soon.')
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Couldn’t get your location.')
    }
    setFinding(false)
  }

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
        <div className="title-buttons">
          {supported && (
            <button
              type="button"
              className="round-button"
              onClick={whoIsHere}
              disabled={finding}
              aria-label="Who’s this house? Find the customer at your location"
            >
              <MapPin size={20} weight="fill" aria-hidden="true" />
            </button>
          )}
          <Link to="/customers/new" className="round-button" aria-label="Add a customer">
            <Plus size={20} weight="bold" aria-hidden="true" />
          </Link>
        </div>
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
          <p className="empty">No customers yet. Tap + to add your first one.</p>
        ))}

      <ul className="rows">
        {customers?.map((c) => (
          <li key={c.id}>
            <Row
              to={`/customers/${c.id}`}
              title={c.name}
              sub={
                <>
                  {c.properties[0] && c.properties[0].city}
                  {c.properties.length > 1 && ` +${c.properties.length - 1}`}
                  {' · '}
                  <span className="time">{formatPhone(c.phone)}</span>
                </>
              }
            />
          </li>
        ))}
      </ul>

      {searching && customers?.length === MAX_RESULTS && (
        <p className="muted center">Showing the first {MAX_RESULTS}. Type more to narrow it down.</p>
      )}
    </main>
  )
}
