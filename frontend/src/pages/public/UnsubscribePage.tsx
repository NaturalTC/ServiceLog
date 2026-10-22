import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { apiFetch, errorMessage } from '../../api/client'

// Public page behind the link in reminder emails. Opening it changes nothing: email
// security scanners open every link, so unsubscribing takes a tap on the button.
export function UnsubscribePage() {
  const token = useSearchParams()[0].get('token')
  const [done, setDone] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function unsubscribe() {
    setBusy(true)
    setError(null)
    try {
      const { message } = await apiFetch<{ message: string }>('/api/unsubscribe', {
        method: 'POST',
        body: JSON.stringify({ token }),
      })
      setDone(message)
    } catch (err) {
      setError(errorMessage(err))
    }
    setBusy(false)
  }

  return (
    <main className="auth">
      <h1>Tune-up reminders</h1>
      {done ? (
        <p className="notice" role="status">
          {done} If you change your mind, just reply to one of our emails or give us a call.
        </p>
      ) : !token ? (
        <p className="error">This link is incomplete. Please use the link from the email.</p>
      ) : (
        <>
          <p>Stop getting tune-up reminder emails? We'll still be here when you need us.</p>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button type="button" onClick={unsubscribe} disabled={busy}>
            {busy ? 'Unsubscribing…' : 'Unsubscribe'}
          </button>
        </>
      )}
    </main>
  )
}
