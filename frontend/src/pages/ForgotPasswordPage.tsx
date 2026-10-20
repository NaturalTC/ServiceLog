import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { authApi } from '../api/auth'
import { errorMessage } from '../api/client'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sentMessage, setSentMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const { message } = await authApi.forgotPassword(email)
      setSentMessage(message)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth">
      <h1>Reset password</h1>
      {sentMessage ? (
        <p className="notice" role="status">
          {sentMessage}
        </p>
      ) : (
        <form onSubmit={handleSubmit} noValidate>
          <p>Enter your email and we'll send you a link to set a new password.</p>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            autoComplete="username"
            inputMode="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" disabled={submitting || !email}>
            {submitting ? 'Sending…' : 'Send reset link'}
          </button>
        </form>
      )}
      <p className="links">
        <Link to="/login">Back to log in</Link>
      </p>
    </main>
  )
}
