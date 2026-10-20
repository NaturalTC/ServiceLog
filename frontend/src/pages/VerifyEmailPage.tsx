import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { authApi } from '../api/auth'
import { errorMessage } from '../api/client'

type State = { kind: 'verifying' } | { kind: 'done'; message: string } | { kind: 'failed'; message: string }

export function VerifyEmailPage() {
  const token = useSearchParams()[0].get('token')
  const [state, setState] = useState<State>(
    token ? { kind: 'verifying' } : { kind: 'failed', message: 'This link is missing its code.' },
  )
  // Tokens are single-use, so make sure React's development double-render doesn't send it twice
  const sent = useRef(false)

  useEffect(() => {
    if (!token || sent.current) return
    sent.current = true
    authApi
      .verifyEmail(token)
      .then(({ message }) => setState({ kind: 'done', message }))
      .catch((err) => setState({ kind: 'failed', message: errorMessage(err) }))
  }, [token])

  return (
    <main className="auth">
      <h1>Verify email</h1>
      {state.kind === 'verifying' && <p className="loading">Verifying…</p>}
      {state.kind === 'done' && (
        <p className="notice" role="status">
          {state.message}
        </p>
      )}
      {state.kind === 'failed' && (
        <p className="error" role="alert">
          {state.message} Log in to get a new verification email.
        </p>
      )}
      <p className="links">
        <Link to="/login">Go to log in</Link>
      </p>
    </main>
  )
}
