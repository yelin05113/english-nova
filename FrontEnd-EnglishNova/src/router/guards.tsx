import { Navigate, Outlet } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'

export function ProtectedGuard() {
  const { token, user } = useAppStateContext()

  if (!token) return <Navigate to="/auth" replace />
  if (!user) return null

  return <Outlet />
}

export function AuthGuard() {
  const { token, user } = useAppStateContext()

  if (token && !user) return null
  if (user) return <Navigate to="/" replace />

  return <Outlet />
}
