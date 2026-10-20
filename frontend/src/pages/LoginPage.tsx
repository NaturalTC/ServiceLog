import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { authApi } from '../api/auth'
import { ApiError, errorMessage } from '../api/client'
import { useAuth } from '../auth/authContext'

export function LoginPage() {
  const { login } = useAuth()
  // Pages like "password changed" send a confirmation message here
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [needsVerification, setNeedsVerification] = useState(false)
  const [resent, setResent] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setNeedsVerification(false)
    setResent(false)
    setSubmitting(true)
    try {
      await login(email, password)
    } catch (err) {
      setNeedsVerification(err instanceof ApiError && err.code === 'EMAIL_NOT_VERIFIED')
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function resendVerification() {
    try {
      await authApi.resendVerification(email)
      setResent(true)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <main className="auth">
      <h1>ServiceLog</h1>
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      <form onSubmit={handleSubmit} noValidate>
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

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        {needsVerification &&
          (resent ? (
            <p className="notice" role="status">
              We sent a new verification link. Check your inbox.
            </p>
          ) : (
            <button type="button" className="secondary" onClick={resendVerification}>
              Resend verification email
            </button>
          ))}

        <button type="submit" disabled={submitting || !email || !password}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p className="links">
        <Link to="/forgot-password">Forgot password?</Link>
      </p>
    </main>
  )
}
