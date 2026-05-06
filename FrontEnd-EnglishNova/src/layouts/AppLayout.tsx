import { Outlet } from 'react-router'
import { AuthModal } from '../components/AuthModal'
import { useAppStateContext } from '../context/AppStateContext'
import { Topbar } from '../components/Topbar'

export function AppLayout() {
  const { error, message, flashNotice, layoutMode } = useAppStateContext()

  return (
    <div className={`app-shell layout-${layoutMode}`}>
      <Topbar />
      {(flashNotice || error || message) && (
        <div className="toast-layer" aria-live="polite" aria-atomic="true">
          {flashNotice && <p className={`notice ${flashNotice.type === 'error' ? 'error' : 'success'}`}>{flashNotice.text}</p>}
          {error && <p className="notice error">{error}</p>}
          {message && <p className="notice success">{message}</p>}
        </div>
      )}
      <main className="view-content">
        <Outlet />
      </main>
      <AuthModal />
    </div>
  )
}
