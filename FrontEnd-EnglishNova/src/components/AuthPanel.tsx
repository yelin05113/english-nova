import type { SystemOverview } from '../types'

interface AuthPanelProps {
  authTab: 'login' | 'register'
  setAuthTab: (tab: 'login' | 'register') => void
  account: string
  setAccount: (value: string) => void
  loginPassword: string
  setLoginPassword: (value: string) => void
  registerUsername: string
  setRegisterUsername: (value: string) => void
  registerEmail: string
  setRegisterEmail: (value: string) => void
  registerPassword: string
  setRegisterPassword: (value: string) => void
  overview: SystemOverview | null
  onLogin: () => void
  onRegister: () => void
}

export function AuthPanel({
  authTab,
  setAuthTab,
  account,
  setAccount,
  loginPassword,
  setLoginPassword,
  registerUsername,
  setRegisterUsername,
  registerEmail,
  setRegisterEmail,
  registerPassword,
  setRegisterPassword,
  overview,
  onLogin,
  onRegister,
}: AuthPanelProps) {
  return (
    <section className="auth-layout">
      <article className="panel">
        <div className="tab-row">
          <button
            type="button"
            className={authTab === 'login' ? 'tab active' : 'tab'}
            onClick={() => setAuthTab('login')}
          >
            登录
          </button>
          <button
            type="button"
            className={authTab === 'register' ? 'tab active' : 'tab'}
            onClick={() => setAuthTab('register')}
          >
            注册
          </button>
        </div>
        {authTab === 'login' ? (
          <div className="form">
            <label>
              <span>用户名或邮箱</span>
              <input value={account} onChange={(e) => setAccount(e.target.value)} />
            </label>
            <label>
              <span>密码</span>
              <input
                type="password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
              />
            </label>
            <button type="button" className="primary" onClick={() => void onLogin()}>
              登录并进入词库
            </button>
          </div>
        ) : (
          <div className="form">
            <label>
              <span>用户名</span>
              <input
                value={registerUsername}
                onChange={(e) => setRegisterUsername(e.target.value)}
              />
            </label>
            <label>
              <span>邮箱</span>
              <input
                value={registerEmail}
                onChange={(e) => setRegisterEmail(e.target.value)}
              />
            </label>
            <label>
              <span>密码</span>
              <input
                type="password"
                value={registerPassword}
                onChange={(e) => setRegisterPassword(e.target.value)}
              />
            </label>
            <button type="button" className="primary" onClick={() => void onRegister()}>
              注册并创建私有空间
            </button>
          </div>
        )}
      </article>
      <article className="panel">
        <p className="eyebrow">系统模块</p>
        <div className="list">
          {(overview?.modules ?? []).map((item) => (
            <div key={item.name} className="row">
              <strong>{item.name}</strong>
              <span>{item.responsibility}</span>
            </div>
          ))}
        </div>
      </article>
    </section>
  )
}
