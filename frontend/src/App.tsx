import { Navigate, Route, Routes } from 'react-router'
import { useAuth } from './auth/authContext'
import { AppLayout } from './components/AppLayout'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { LoginPage } from './pages/LoginPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'
import { CustomerDetailPage } from './pages/customers/CustomerDetailPage'
import { CustomerFormPage } from './pages/customers/CustomerFormPage'
import { CustomerListPage } from './pages/customers/CustomerListPage'

function App() {
  const { status } = useAuth()

  if (status === 'checking') return <p className="loading">Loading…</p>
  const loggedIn = status === 'logged-in'

  return (
    <Routes>
      {/* Pages that need a login send you to /login */}
      <Route element={loggedIn ? <AppLayout /> : <Navigate to="/login" replace />}>
        {/* Customers is the home screen until "My day" (S8) exists */}
        <Route path="/" element={<Navigate to="/customers" replace />} />
        <Route path="/customers" element={<CustomerListPage />} />
        <Route path="/customers/new" element={<CustomerFormPage />} />
        <Route path="/customers/:id" element={<CustomerDetailPage />} />
        <Route path="/customers/:id/edit" element={<CustomerFormPage />} />
      </Route>

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
