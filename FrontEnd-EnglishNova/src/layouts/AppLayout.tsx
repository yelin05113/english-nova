import { Navigate, Outlet } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'
import { Topbar } from '../components/Topbar'

export function AppLayout() {
  const { user, error, message } = useAppStateContext()

  if (!user) return <Navigate to="/auth" replace />

  return (
    <div className="w-[min(1360px,calc(100%-24px))] mx-auto pt-[22px] pb-14">
      <Topbar />
      {error && (
        <p className="mt-[18px] px-[14px] py-3 rounded-2xl bg-[rgba(207,118,94,0.16)] text-[#8b3e30]">
          {error}
        </p>
      )}
      {message && (
        <p className="mt-[18px] px-[14px] py-3 rounded-2xl bg-[rgba(104,152,104,0.16)] text-[#375e3d]">
          {message}
        </p>
      )}
      <main className="mt-5">
        <Outlet />
      </main>
    </div>
  )
}
