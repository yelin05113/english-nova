import { Navigate, Outlet } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'

export function ProtectedGuard() {
  const { token, user } = useAppStateContext()

  if (!token) return <Navigate to="/" replace />
  if (!user) return null

  return <Outlet />
}
