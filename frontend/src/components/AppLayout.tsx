import { Briefcase, CalendarCheck, ChatCircleDots, CheckCircle, SignOut, Users, Wrench } from '@phosphor-icons/react'
import { useEffect } from 'react'
import { NavLink, Outlet } from 'react-router'
import { toast, Toaster } from 'sonner'
import { useAuth } from '../auth/authContext'
import { CACHE, offlineStore } from '../lib/offlineStore'
import { startSyncing, syncQueue, useSyncQueue } from '../lib/syncQueue'
import { useConfirm } from './ConfirmSheet'
import { OfflineStatus } from './OfflineStatus'
import { CHAT_KEY } from '../pages/assistant/AssistantPage'

// Shown on every page after login: a slim top bar, and a tab bar at the bottom of the
// screen where a thumb can reach it (like a native phone app). On wide screens the tabs
// sit in a floating bar at the top instead.
const TABS = [
  { to: '/', label: 'Today', icon: CalendarCheck, end: true },
  { to: '/jobs', label: 'Jobs', icon: Briefcase, end: false },
  { to: '/assistant', label: 'Ask', icon: ChatCircleDots, end: false },
  { to: '/tune-ups', label: 'Tune-ups', icon: Wrench, end: false },
  { to: '/customers', label: 'Customers', icon: Users, end: false },
]

export function AppLayout() {
  const { logout } = useAuth()
  const queue = useSyncQueue()
  const [ask, sheet] = useConfirm()

  // Send anything done without signal as soon as it can go
  useEffect(startSyncing, [])

  // Say so when things done without signal reach the server
  useEffect(() => {
    const onSynced = (e: Event) => {
      const sent = (e as CustomEvent<number>).detail
      toast.success(`Synced ${sent} change${sent === 1 ? '' : 's'} made without signal`)
    }
    window.addEventListener('servicelog:synced', onSynced)
    return () => window.removeEventListener('servicelog:synced', onSynced)
  }, [])

  // Out of the way in the corner, and confirmed, so it's never tapped by accident on a job.
  // Logging out wipes everything saved on the phone, including anything not yet synced.
  async function confirmLogout() {
    const unsynced = queue.length > 0
    const ok = await ask(
      unsynced
        ? {
            title: 'Log out and lose unsynced changes?',
            message: `${queue.length} change${queue.length === 1 ? " hasn't" : "s haven't"} synced yet. Logging out deletes ${queue.length === 1 ? 'it' : 'them'} from this phone.`,
            confirmLabel: 'Log out anyway',
            cancelLabel: 'Stay logged in',
            danger: true,
          }
        : { title: 'Log out of ServiceLog?', confirmLabel: 'Log out', cancelLabel: 'Stay logged in' },
    )
    if (!ok) return
    await syncQueue.clear()
    await offlineStore.clear(CACHE).catch(() => {})
    sessionStorage.removeItem(CHAT_KEY)
    logout()
  }

  return (
    <>
      <header className="app-header">
        <NavLink to="/" className="brand">
          <img src="/icons/favicon-32.png" alt="" width="24" height="24" />
          ServiceLog
        </NavLink>
        <nav className="tabs" aria-label="Main">
          {TABS.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end}>
              <span className="tab-icon">
                <Icon size={22} aria-hidden="true" />
              </span>
              {label}
            </NavLink>
          ))}
        </nav>
        <button type="button" className="icon-button" onClick={confirmLogout} aria-label="Log out">
          <SignOut size={20} aria-hidden="true" />
          <span className="wide-only">Log out</span>
        </button>
      </header>
      <div className="app-content">
        <OfflineStatus />
        <Outlet />
      </div>
      {sheet}
      <Toaster
        position="bottom-center"
        offset={{ bottom: 24 }}
        mobileOffset={{ bottom: 'calc(88px + env(safe-area-inset-bottom))', left: 12, right: 12 }}
        icons={{ success: <CheckCircle size={22} weight="fill" /> }}
        toastOptions={{
          unstyled: true,
          duration: 5000,
          classNames: {
            toast: 'toast',
            icon: 'toast-icon',
            content: 'toast-content',
            title: 'toast-title',
            description: 'toast-description',
            actionButton: 'toast-action',
          },
        }}
      />
    </>
  )
}
