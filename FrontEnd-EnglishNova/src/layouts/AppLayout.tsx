import { Outlet } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'
import { Topbar } from '../components/Topbar'

export function AppLayout() {
  const { error, message } = useAppStateContext()

  return (
    <div className="app-shell">
      <Topbar />
      {error && <p className="notice error">{error}</p>}
      {message && <p className="notice success">{message}</p>}
      <main className="view-content">
        <Outlet />
      </main>
    </div>
  )
}
