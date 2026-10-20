import { useEffect, useState } from 'react'
import './App.css'

// Starter screen: proves the frontend can reach the backend.
// TODO(you): replace with the "My day" screen - see docs/PLAN.md.
function App() {
  const [status, setStatus] = useState<string>('checking...')

  useEffect(() => {
    fetch('/actuator/health')
      .then((res) => res.json())
      .then((body: { status: string }) => setStatus(body.status))
      .catch(() => setStatus('backend not reachable - is Spring Boot running?'))
  }, [])

  return (
    <main>
      <h1>ServiceLog</h1>
      <p>HVAC service history and tune-up reminders for one-person shops.</p>
      <p>Backend: {status}</p>
    </main>
  )
}

export default App
