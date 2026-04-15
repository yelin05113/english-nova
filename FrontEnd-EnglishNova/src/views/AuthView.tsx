import { Navigate } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'

export function AuthView() {
  const {
    user,
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
    error,
    handleLogin,
    handleRegister,
  } = useAppStateContext()

  if (user) return <Navigate to="/" replace />

  return (
    <div className="auth-page">
      <div className="auth-brand">
        <span className="logo">1103</span>
        <div>
          <p className="eyebrow">English Nova</p>
          <h1>1103 单词控制台</h1>
        </div>
      </div>

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

          {error && <p className="notice error">{error}</p>}

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
                  onKeyDown={(e) => e.key === 'Enter' && void handleLogin()}
                />
              </label>
              <button type="button" className="primary" onClick={() => void handleLogin()}>
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
                  type="email"
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
              <button type="button" className="primary" onClick={() => void handleRegister()}>
                注册并开始使用
              </button>
            </div>
          )}
        </article>

        {overview && (
          <article className="panel auth-overview">
            <p className="eyebrow">平台功能</p>
            <h2 className="auth-product-name">{overview.productName}</h2>
            <p>{overview.theme}</p>
            <div className="auth-platforms">
              {overview.supportedPlatforms.map((p) => (
                <span key={p} className="badge">{p}</span>
              ))}
            </div>
          </article>
        )}
      </section>
    </div>
  )
}
