import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { authApi } from '../api/auth'
import { errorMessage } from '../api/client'

const MIN_PASSWORD_LENGTH = 12

export function ResetPasswordPage() {
  const token = useSearchParams()[0].get('token')
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const tooShort = password.length > 0 && password.length < MIN_PASSWORD_LENGTH
  const mismatch = confirm.length > 0 && password !== confirm

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setError(null)
    setSubmitting(true)
    try {
      const { message } = await authApi.resetPassword(token, password)
      navigate('/login', { replace: true, state: { notice: message } })
    } catch (err) {
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  if (!token) {
    return (
      <main className="auth">
        <h1>Reset password</h1>
        <p className="error">This link is missing its code. Request a new one.</p>
        <p className="links">
          <Link to="/forgot-password">Request a new link</Link>
        </p>
      </main>
    )
  }

  return (
    <main className="auth">
      <h1>Choose a new password</h1>
      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="password">New password</label>
        <input
          id="password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-describedby="password-hint"
          required
        />
        <p id="password-hint" className={tooShort ? 'error' : 'hint'}>
          At least {MIN_PASSWORD_LENGTH} characters.
        </p>

        <label htmlFor="confirm">Confirm new password</label>
        <input
          id="confirm"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          required
        />
        {mismatch && <p className="error">Passwords don't match.</p>}

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={submitting || password.length < MIN_PASSWORD_LENGTH || password !== confirm}
        >
          {submitting ? 'Saving…' : 'Set new password'}
        </button>
      </form>
      <p className="links">
        <Link to="/forgot-password">Request a new link</Link>
      </p>
    </main>
  )
}
