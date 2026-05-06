import { useEffect, useState, type MouseEvent } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { navItems } from '../constants'
import { useAppStateContext } from '../context/AppStateContext'
import { UserAvatar } from './UserAvatar'

export function Topbar() {
  const location = useLocation()
  const { user, clearAuth, loading, openAuthModal } = useAppStateContext()
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    setMenuOpen(false)
  }, [location.pathname, location.search])

  function handleNavClick(requiresAuth?: boolean) {
    return (event: MouseEvent<HTMLAnchorElement>) => {
      if (!user && requiresAuth) {
        event.preventDefault()
        setMenuOpen(false)
        openAuthModal()
        return
      }
      setMenuOpen(false)
    }
  }

  return (
    <header className="topbar">
      <Link to="/" className="brand">
        <span className="logo">1103</span>
        <div className="brand-copy">
          <p className="eyebrow">像素词库</p>
          <h1>1103 单词控制台</h1>
        </div>
      </Link>

      <button
        type="button"
        className={`topbar-menu-toggle${menuOpen ? ' active' : ''}`}
        aria-label={menuOpen ? '收起导航菜单' : '展开导航菜单'}
        aria-expanded={menuOpen}
        aria-controls="topbar-nav"
        onClick={() => setMenuOpen((current) => !current)}
      >
        <span />
        <span />
        <span />
      </button>

      <div className={`topbar-nav-shell${menuOpen ? ' open' : ''}`}>
        <nav id="topbar-nav" className="top-nav" aria-label="主导航">
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) => (isActive ? 'top-nav-link active' : 'top-nav-link')}
              onClick={handleNavClick(item.requiresAuth)}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </div>

      <div className="topbar-actions">
        {loading ? <span className="badge">同步中</span> : null}
        {user ? (
          <div className="bili-user-entry">
            <button type="button" className="bili-user-trigger" aria-label={`${user.username} 的用户菜单`}>
              <UserAvatar user={user} className="bili-avatar" />
            </button>
            <div className="bili-user-popover">
              <span className="bili-popover-name">{user.username}</span>
              <Link className="bili-profile-link" to="/profile">
                个人中心
              </Link>
              <Link className="bili-profile-link" to="/">
                回到主页
              </Link>
              <button
                type="button"
                className="bili-logout-button"
                onClick={() => clearAuth({ notice: { type: 'error', text: '成功退出' } })}
              >
                退出登录
              </button>
            </div>
          </div>
        ) : (
          <button type="button" className="auth-entry-button" onClick={() => openAuthModal()}>
            登录 / 注册
          </button>
        )}
      </div>
    </header>
  )
}
