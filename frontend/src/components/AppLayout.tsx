import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/authContext'

// Header shown on every page after login
export function AppLayout() {
  const { logout } = useAuth()

  return (
    <>
      <header className="app-header">
        <NavLink to="/" className="brand">
          ServiceLog
        </NavLink>
        <nav>
          <NavLink to="/" end>
            Today
          </NavLink>
          <NavLink to="/jobs">Jobs</NavLink>
          <NavLink to="/tune-ups">Tune-ups</NavLink>
          <NavLink to="/customers">Customers</NavLink>
          <button type="button" className="link-button" onClick={logout}>
            Log out
          </button>
        </nav>
      </header>
      <Outlet />
    </>
  )
}
