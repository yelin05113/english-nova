import { Link, NavLink } from 'react-router'
import { navItems } from '../constants'
import { useAppStateContext } from '../context/AppStateContext'
import { UserAvatar } from './UserAvatar'

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
          <div className="bili-user-entry">
            <button type="button" className="bili-user-trigger" aria-label={`${user.username} 的用户菜单`}>
              <UserAvatar user={user} className="bili-avatar" />
            </button>
            <div className="bili-user-popover">
              <span className="bili-popover-name">{user.username}</span>
              <Link className="bili-profile-link" to="/profile">
                个人中心
              </Link>
              <button type="button" className="bili-logout-button" onClick={clearAuth}>
                退出登录
              </button>
            </div>
          </div>
        )}
      </div>
    </header>
  )
}
