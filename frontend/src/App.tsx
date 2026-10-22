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
import { FinishJobPage } from './pages/jobs/FinishJobPage'
import { JobDetailPage } from './pages/jobs/JobDetailPage'
import { JobFormPage } from './pages/jobs/JobFormPage'
import { JobListPage } from './pages/jobs/JobListPage'
import { LandingPage } from './pages/public/LandingPage'
import { UnsubscribePage } from './pages/public/UnsubscribePage'
import { MyDayPage } from './pages/today/MyDayPage'
import { TuneUpsPage } from './pages/tuneups/TuneUpsPage'
import { WarrantyPage } from './pages/warranty/WarrantyPage'
import { SyncPage } from './pages/sync/SyncPage'
import { ServiceRecordDetailPage } from './pages/service/ServiceRecordDetailPage'
import { ServiceRecordFormPage } from './pages/service/ServiceRecordFormPage'
import { Loading } from './components/Loading'

function App() {
  const { status } = useAuth()

  if (status === 'checking') return <Loading />
  const loggedIn = status === 'logged-in'

  return (
    <Routes>
      {/* "/" is the app's home (My day) when logged in, and the public landing page otherwise */}
      <Route path="/" element={loggedIn ? <AppLayout /> : <LandingPage />}>
        <Route index element={<MyDayPage />} />
      </Route>

      {/* Pages that need a login send you to /login */}
      <Route element={loggedIn ? <AppLayout /> : <Navigate to="/login" replace />}>
        <Route path="/jobs" element={<JobListPage />} />
        <Route path="/jobs/new" element={<JobFormPage />} />
        <Route path="/jobs/:id" element={<JobDetailPage />} />
        <Route path="/jobs/:id/edit" element={<JobFormPage />} />
        <Route path="/jobs/:id/finish" element={<FinishJobPage />} />
        <Route path="/tune-ups" element={<TuneUpsPage />} />
        <Route path="/customers" element={<CustomerListPage />} />
        <Route path="/customers/new" element={<CustomerFormPage />} />
        <Route path="/customers/:id" element={<CustomerDetailPage />} />
        <Route path="/customers/:id/edit" element={<CustomerFormPage />} />
        <Route path="/properties/:propertyId/equipment/new" element={<EquipmentFormPage />} />
        <Route path="/equipment/:id" element={<EquipmentDetailPage />} />
        <Route path="/equipment/:id/edit" element={<EquipmentFormPage />} />
        <Route path="/equipment/:equipmentId/service-records/new" element={<ServiceRecordFormPage />} />
        <Route path="/warranty" element={<WarrantyPage />} />
        <Route path="/sync" element={<SyncPage />} />
        <Route path="/service-records/:id" element={<ServiceRecordDetailPage />} />
        <Route path="/service-records/:id/edit" element={<ServiceRecordFormPage />} />
      </Route>

      <Route path="/login" element={loggedIn ? <Navigate to="/" replace /> : <LoginPage />} />
      {/* Email links: these work whether or not you're logged in */}
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      {/* For customers, from reminder emails: no login */}
      <Route path="/unsubscribe" element={<UnsubscribePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
