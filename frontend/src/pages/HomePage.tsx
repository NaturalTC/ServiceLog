import { useAuth } from '../auth/authContext'

// Placeholder until "My day" (S8). Shows the app is behind a login.
export function HomePage() {
  const { email, logout } = useAuth()

  return (
    <main className="home">
      <header>
        <h1>ServiceLog</h1>
        <button type="button" className="secondary" onClick={logout}>
          Log out
        </button>
      </header>
      <p>Logged in as {email}</p>
    </main>
  )
}
