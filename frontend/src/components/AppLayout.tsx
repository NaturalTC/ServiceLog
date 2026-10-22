import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/authContext'

// Shown on every page after login: a slim top bar, and a tab bar at the bottom of the
// screen where a thumb can reach it (like a native phone app). On wide screens the tabs
// sit in the top bar instead.
export function AppLayout() {
  const { logout } = useAuth()

  return (
    <>
      <header className="app-header">
        <NavLink to="/" className="brand">
          ServiceLog
        </NavLink>
        <nav className="tabs" aria-label="Main">
          <NavLink to="/" end>
            Today
          </NavLink>
          <NavLink to="/jobs">Jobs</NavLink>
          <NavLink to="/tune-ups">Tune-ups</NavLink>
          <NavLink to="/customers">Customers</NavLink>
        </nav>
        <button type="button" className="link-button" onClick={logout}>
          Log out
        </button>
      </header>
      <div className="app-content">
        <Outlet />
      </div>
    </>
  )
}
