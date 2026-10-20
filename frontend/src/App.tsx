import { useAuth } from './auth/authContext'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'

function App() {
  const { status } = useAuth()

  if (status === 'checking') return <p className="loading">Loading…</p>
  return status === 'logged-in' ? <HomePage /> : <LoginPage />
}

export default App
