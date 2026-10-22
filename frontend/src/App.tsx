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
import { EquipmentDetailPage } from './pages/equipment/EquipmentDetailPage'
import { EquipmentFormPage } from './pages/equipment/EquipmentFormPage'
import { JobDetailPage } from './pages/jobs/JobDetailPage'
import { JobFormPage } from './pages/jobs/JobFormPage'
import { JobListPage } from './pages/jobs/JobListPage'
import { MyDayPage } from './pages/today/MyDayPage'
import { ServiceRecordDetailPage } from './pages/service/ServiceRecordDetailPage'
import { ServiceRecordFormPage } from './pages/service/ServiceRecordFormPage'

function App() {
  const { status } = useAuth()

  if (status === 'checking') return <p className="loading">Loading…</p>
  const loggedIn = status === 'logged-in'

  return (
    <Routes>
      {/* Pages that need a login send you to /login */}
      <Route element={loggedIn ? <AppLayout /> : <Navigate to="/login" replace />}>
        {/* The home screen: today's jobs */}
        <Route path="/" element={<MyDayPage />} />
        <Route path="/jobs" element={<JobListPage />} />
        <Route path="/jobs/new" element={<JobFormPage />} />
        <Route path="/jobs/:id" element={<JobDetailPage />} />
        <Route path="/jobs/:id/edit" element={<JobFormPage />} />
        <Route path="/customers" element={<CustomerListPage />} />
        <Route path="/customers/new" element={<CustomerFormPage />} />
        <Route path="/customers/:id" element={<CustomerDetailPage />} />
        <Route path="/customers/:id/edit" element={<CustomerFormPage />} />
        <Route path="/properties/:propertyId/equipment/new" element={<EquipmentFormPage />} />
        <Route path="/equipment/:id" element={<EquipmentDetailPage />} />
        <Route path="/equipment/:id/edit" element={<EquipmentFormPage />} />
        <Route path="/equipment/:equipmentId/service-records/new" element={<ServiceRecordFormPage />} />
        <Route path="/service-records/:id" element={<ServiceRecordDetailPage />} />
        <Route path="/service-records/:id/edit" element={<ServiceRecordFormPage />} />
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
