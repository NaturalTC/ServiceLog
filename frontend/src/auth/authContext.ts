import { createContext, useContext } from 'react'

export type AuthStatus = 'checking' | 'logged-out' | 'logged-in'

export interface AuthState {
  status: AuthStatus
  email: string | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}
