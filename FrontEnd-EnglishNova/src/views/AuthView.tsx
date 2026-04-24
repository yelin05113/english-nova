import type { FormEvent } from 'react'
import { useAppStateContext } from '../context/AppStateContext'

export function AuthView() {
  const {
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
    loading,
    handleLogin,
    handleRegister,
  } = useAppStateContext()

  function onLoginSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (loading) return
    void handleLogin()
  }

  function onRegisterSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (loading) return
    void handleRegister()
  }

  return (
    <div className="auth-page">
      <div className="auth-brand">
        <span className="logo">1103</span>
        <div>
          <p className="eyebrow">English Nova</p>
          <h1>单词学习控制台</h1>
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
            <form className="form" onSubmit={onLoginSubmit}>
              <label>
                <span>用户名或邮箱</span>
                <input
                  value={account}
                  onChange={(e) => setAccount(e.target.value)}
                  placeholder="请输入用户名或邮箱"
                  autoComplete="username"
                />
              </label>
              <label>
                <span>密码</span>
                <input
                  type="password"
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  placeholder="请输入密码"
                  autoComplete="current-password"
                />
              </label>
              <button type="submit" className="primary" disabled={loading}>
                {loading ? '登录中...' : '登录并进入词库'}
              </button>
            </form>
          ) : (
            <form className="form" onSubmit={onRegisterSubmit}>
              <label>
                <span>用户名</span>
                <input
                  value={registerUsername}
                  onChange={(e) => setRegisterUsername(e.target.value)}
                  placeholder="请输入用户名"
                  autoComplete="username"
                />
              </label>
              <label>
                <span>邮箱</span>
                <input
                  type="email"
                  value={registerEmail}
                  onChange={(e) => setRegisterEmail(e.target.value)}
                  placeholder="请输入邮箱"
                  autoComplete="email"
                />
              </label>
              <label>
                <span>密码</span>
                <input
                  type="password"
                  value={registerPassword}
                  onChange={(e) => setRegisterPassword(e.target.value)}
                  placeholder="请设置密码"
                  autoComplete="new-password"
                />
              </label>
              <button type="submit" className="primary" disabled={loading}>
                {loading ? '注册中...' : '注册并开始使用'}
              </button>
            </form>
          )}
        </article>

        {overview && (
          <article className="panel auth-overview">
            <p className="eyebrow">平台能力</p>
            <h2 className="auth-product-name">{overview.productName}</h2>
            <p>{overview.theme}</p>
            <div className="auth-platforms">
              {overview.supportedPlatforms.map((p) => (
                <span key={p} className="badge">
                  {p}
                </span>
              ))}
            </div>
          </article>
        )}
      </section>
    </div>
  )
}
