import { NavLink } from 'react-router'
import { navItems } from '../constants'
import { useAppStateContext } from '../context/AppStateContext'

export function Topbar() {
  const { user, clearAuth, loading } = useAppStateContext()

  return (
    <header className="topbar">
      <div className="brand">
        <span className="logo">1103</span>
        <div>
          <p className="eyebrow">English Nova</p>
          <h1>1103 单词控制台</h1>
        </div>
      </div>

      <nav className="top-nav">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) => (isActive ? 'top-nav-link active' : 'top-nav-link')}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="topbar-actions">
        {loading && <span className="badge">同步中</span>}
        {user && (
          <>
            <span className="user-chip">{user.username}</span>
            <button type="button" className="ghost" onClick={clearAuth}>
              退出
            </button>
          </>
        )}
      </div>
    </header>
  )
}
