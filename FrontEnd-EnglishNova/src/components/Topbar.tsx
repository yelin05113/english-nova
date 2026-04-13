import type { AuthUser } from '../types'

interface TopbarProps {
  user: AuthUser | null
  onLogout: () => void
}

export function Topbar({ user, onLogout }: TopbarProps) {
  return (
    <header className="topbar">
      <div className="brand">
        <span className="logo">1103</span>
        <div>
          <p className="eyebrow">English Nova</p>
          <h1>1103 单词控制台</h1>
        </div>
      </div>
      {user ? (
        <button type="button" className="ghost" onClick={onLogout}>
          退出登录
        </button>
      ) : (
        <p className="eyebrow">登录后开启个人词库</p>
      )}
    </header>
  )
}
