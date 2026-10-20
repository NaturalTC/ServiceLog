import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiFetch, setUnauthorizedHandler, tokenStore } from '../api/client'
import { AuthContext, type AuthStatus } from './authContext'

interface LoginResponse {
  token: string
  expiresInSeconds: number
}

interface CurrentUser {
  email: string
}

// Holds who is logged in and exposes login/logout to the rest of the app
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>(tokenStore.get() ? 'checking' : 'logged-out')
  const [email, setEmail] = useState<string | null>(null)

  const logout = useCallback(() => {
    tokenStore.clear()
    setEmail(null)
    setStatus('logged-out')
  }, [])

  // Any 401 from the API (e.g. the token expired mid-day) sends the owner back to login
  useEffect(() => {
    setUnauthorizedHandler(logout)
  }, [logout])

  // On app start, check that a saved token is still valid
  useEffect(() => {
    if (!tokenStore.get()) return
    apiFetch<CurrentUser>('/api/auth/me')
      .then((user) => {
        setEmail(user.email)
        setStatus('logged-in')
      })
      .catch(logout)
  }, [logout])

  const login = useCallback(async (loginEmail: string, password: string) => {
    const { token } = await apiFetch<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: loginEmail, password }),
    })
    tokenStore.set(token)
    const user = await apiFetch<CurrentUser>('/api/auth/me')
    setEmail(user.email)
    setStatus('logged-in')
  }, [])

  const value = useMemo(() => ({ status, email, login, logout }), [status, email, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
