import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { useAuth } from './auth/authContext'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'

function App() {
  const { status } = useAuth()

  if (status === 'checking') return <p className="loading">Loading…</p>
  const loggedIn = status === 'logged-in'

  // Pages that need a login send you to /login; /login sends you home once you're in
  const requireLogin = (page: ReactNode) => (loggedIn ? page : <Navigate to="/login" replace />)

  return (
    <Routes>
      <Route path="/" element={requireLogin(<HomePage />)} />
      <Route path="/login" element={loggedIn ? <Navigate to="/" replace /> : <LoginPage />} />
      {/* Email links: these work whether or not you're logged in */}
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
