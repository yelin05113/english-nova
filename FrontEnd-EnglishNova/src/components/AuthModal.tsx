import { useEffect, useRef, useState, type Dispatch, type FormEvent, type PointerEvent, type SetStateAction } from 'react'
import { useAppStateContext } from '../context/AppStateContext'

type AuthFieldKey = 'account' | 'loginPassword' | 'registerUsername' | 'registerEmail' | 'registerPassword'
type AuthFieldErrors = Partial<Record<AuthFieldKey, string>>

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function AuthModal() {
  const {
    authModalOpen,
    closeAuthModal,
    clearAuthError,
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
    error,
    loading,
    handleLogin,
    handleRegister,
  } = useAppStateContext()
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors>({})
  const backdropPressStarted = useRef(false)

  useEffect(() => {
    if (!authModalOpen) {
      return
    }

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closeAuthModal()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [authModalOpen, closeAuthModal])

  useEffect(() => {
    if (!authModalOpen) {
      setFieldErrors({})
      return
    }

    setFieldErrors({})
    clearAuthError()
    // clearAuthError 来自上下文，会随应用状态渲染重新创建。
    // 这里只在弹窗打开或登录/注册模式切换时重置字段状态。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authModalOpen, authTab])

  useEffect(() => {
    if (!error) {
      return
    }

    const nextErrors = mapAuthErrorToFieldErrors(authTab, error)
    if (Object.keys(nextErrors).length > 0) {
      setFieldErrors((current) => ({ ...current, ...nextErrors }))
      clearAuthError()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authTab, error])

  if (!authModalOpen) {
    return null
  }

  const isLogin = authTab === 'login'
  const dialogTitleId = isLogin ? 'auth-modal-heading-login' : 'auth-modal-heading-register'

  function switchAuthTab(nextTab: 'login' | 'register') {
    if (loading || nextTab === authTab) {
      return
    }
    setFieldErrors({})
    clearAuthError()
    setAuthTab(nextTab)
  }

  function onLoginSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (loading) return
    const nextErrors = validateLoginFields(account, loginPassword)
    setFieldErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) {
      return
    }
    clearAuthError()
    void handleLogin()
  }

  function onRegisterSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (loading) return
    const nextErrors = validateRegisterFields(registerUsername, registerEmail, registerPassword)
    setFieldErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) {
      return
    }
    clearAuthError()
    void handleRegister()
  }

  function onBackdropPointerDown(event: PointerEvent<HTMLDivElement>) {
    backdropPressStarted.current = event.target === event.currentTarget
  }

  function onBackdropPointerUp(event: PointerEvent<HTMLDivElement>) {
    const shouldClose = backdropPressStarted.current && event.target === event.currentTarget
    backdropPressStarted.current = false
    if (shouldClose) {
      closeAuthModal()
    }
  }

  return (
    <div
      className="auth-modal-backdrop"
      role="presentation"
      onPointerDown={onBackdropPointerDown}
      onPointerUp={onBackdropPointerUp}
      onPointerCancel={() => {
        backdropPressStarted.current = false
      }}
    >
      <section
        className={`auth-modal-shell ${isLogin ? 'login-mode' : 'register-mode'}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={dialogTitleId}
      >
        <button type="button" className="auth-modal-close" aria-label="关闭登录弹窗" onClick={closeAuthModal}>
          ×
        </button>

        <div className="auth-modal-stage">
          <div className="auth-modal-form-layer">
            <section
              className="auth-modal-pane auth-modal-pane--login"
              aria-hidden={!isLogin}
            >
              <span className="auth-modal-kicker">欢迎回来</span>
              <h2 id="auth-modal-heading-login">登录</h2>
              <p className="auth-modal-copy">登录后继续背词、收藏公共词书和同步学习进度。</p>

              <form className="form auth-modal-form" noValidate onSubmit={onLoginSubmit}>
              <label>
                <span>邮箱或用户名</span>
                <input
                  id="modal-login-account"
                  name="account"
                  value={account}
                  className={fieldErrors.account ? 'has-error' : undefined}
                  onBlur={() => setSingleFieldError('account', validateLoginAccount(account), setFieldErrors)}
                  onChange={(event) => {
                    setAccount(event.target.value)
                    clearFieldError('account', setFieldErrors)
                    clearAuthError()
                  }}
                  placeholder="输入邮箱或用户名"
                  autoComplete="username"
                  disabled={!isLogin || loading}
                />
                {fieldErrors.account ? <small className="auth-modal-field-error">{fieldErrors.account}</small> : null}
              </label>
              <label>
                <span>密码</span>
                <input
                  id="modal-login-password"
                  name="password"
                  type="password"
                  value={loginPassword}
                  className={fieldErrors.loginPassword ? 'has-error' : undefined}
                  onBlur={() => setSingleFieldError('loginPassword', validateLoginPassword(loginPassword), setFieldErrors)}
                  onChange={(event) => {
                    setLoginPassword(event.target.value)
                    clearFieldError('loginPassword', setFieldErrors)
                    clearAuthError()
                  }}
                  placeholder="输入密码"
                  autoComplete="current-password"
                  disabled={!isLogin || loading}
                />
                {fieldErrors.loginPassword ? (
                  <small className="auth-modal-field-error">{fieldErrors.loginPassword}</small>
                ) : null}
              </label>
              <button type="submit" className="primary auth-modal-submit" disabled={!isLogin || loading}>
                {loading && isLogin ? '登录中...' : '登录继续'}
                </button>
              </form>
            </section>

            <section
              className="auth-modal-pane auth-modal-pane--register"
              aria-hidden={isLogin}
            >
              <span className="auth-modal-kicker">创建账号</span>
              <h2 id="auth-modal-heading-register">注册</h2>
              <p className="auth-modal-copy">注册后即可开始背词、收藏词书并保存学习记录。</p>

              <form className="form auth-modal-form" noValidate onSubmit={onRegisterSubmit}>
              <label>
                <span>用户名</span>
                <input
                  id="modal-register-username"
                  name="username"
                  value={registerUsername}
                  className={fieldErrors.registerUsername ? 'has-error' : undefined}
                  onBlur={() =>
                    setSingleFieldError('registerUsername', validateRegisterUsername(registerUsername), setFieldErrors)
                  }
                  onChange={(event) => {
                    setRegisterUsername(event.target.value)
                    clearFieldError('registerUsername', setFieldErrors)
                    clearAuthError()
                  }}
                  placeholder="设置用户名"
                  autoComplete="username"
                  disabled={isLogin || loading}
                />
                {fieldErrors.registerUsername ? (
                  <small className="auth-modal-field-error">{fieldErrors.registerUsername}</small>
                ) : null}
              </label>
              <label>
                <span>邮箱</span>
                <input
                  id="modal-register-email"
                  name="email"
                  type="text"
                  value={registerEmail}
                  className={fieldErrors.registerEmail ? 'has-error' : undefined}
                  onBlur={() =>
                    setSingleFieldError('registerEmail', validateRegisterEmail(registerEmail), setFieldErrors)
                  }
                  onChange={(event) => {
                    setRegisterEmail(event.target.value)
                    clearFieldError('registerEmail', setFieldErrors)
                    clearAuthError()
                  }}
                  placeholder="xxx@xx.com"
                  autoComplete="email"
                  disabled={isLogin || loading}
                />
                {fieldErrors.registerEmail ? (
                  <small className="auth-modal-field-error">{fieldErrors.registerEmail}</small>
                ) : null}
              </label>
              <label>
                <span>密码</span>
                <input
                  id="modal-register-password"
                  name="password"
                  type="password"
                  value={registerPassword}
                  className={fieldErrors.registerPassword ? 'has-error' : undefined}
                  onBlur={() =>
                    setSingleFieldError('registerPassword', validateRegisterPassword(registerPassword), setFieldErrors)
                  }
                  onChange={(event) => {
                    setRegisterPassword(event.target.value)
                    clearFieldError('registerPassword', setFieldErrors)
                    clearAuthError()
                  }}
                  placeholder="设置密码"
                  autoComplete="new-password"
                  disabled={isLogin || loading}
                />
                {fieldErrors.registerPassword ? (
                  <small className="auth-modal-field-error">{fieldErrors.registerPassword}</small>
                ) : null}
              </label>
              <button type="submit" className="primary auth-modal-submit" disabled={isLogin || loading}>
                {loading && !isLogin ? '注册中...' : '注册并进入'}
                </button>
              </form>
            </section>
          </div>

          <div className="auth-modal-cover">
            <div className="auth-modal-cover-surface" />
            <div className="auth-modal-cover-reveal">
              <div className="auth-modal-cover-canvas">
                <div className="auth-modal-copy-slot auth-modal-copy-slot--left">
                  <span className="auth-modal-badge">English Nova</span>
                  <h3>已经有账号？</h3>
                  <p>切回登录，继续刚才准备开始的学习动作。</p>
                  <button
                    type="button"
                    className="auth-modal-switch"
                    onClick={() => switchAuthTab('login')}
                    disabled={loading}
                  >
                    去登录
                  </button>
                </div>

                <div className="auth-modal-copy-slot auth-modal-copy-slot--right">
                  <span className="auth-modal-badge">English Nova</span>
                  <h3>还没有账号？</h3>
                  <p>创建一个账号，把公共词书收藏进你的背词台。</p>
                  <button
                    type="button"
                    className="auth-modal-switch"
                    onClick={() => switchAuthTab('register')}
                    disabled={loading}
                  >
                    立即注册
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}

function validateLoginAccount(account: string) {
  return account.trim() ? '' : '请填写邮箱或用户名'
}

function validateLoginPassword(password: string) {
  return password ? '' : '请填写密码'
}

function validateRegisterUsername(username: string) {
  const value = username.trim()
  if (!value) return '请填写用户名'
  if (value.length < 3 || value.length > 32) return '用户名长度应为 3-32 位'
  return ''
}

function validateRegisterEmail(email: string) {
  const value = email.trim()
  if (!value) return '请填写邮箱'
  if (!EMAIL_PATTERN.test(value)) return '请填写正确邮箱，如 xxx@xx.com'
  return ''
}

function validateRegisterPassword(password: string) {
  if (!password) return '请填写密码'
  if (password.length < 6 || password.length > 64) return '密码长度应为 6-64 位'
  return ''
}

function validateLoginFields(account: string, password: string): AuthFieldErrors {
  const nextErrors: AuthFieldErrors = {}
  const accountError = validateLoginAccount(account)
  const passwordError = validateLoginPassword(password)

  if (accountError) nextErrors.account = accountError
  if (passwordError) nextErrors.loginPassword = passwordError

  return nextErrors
}

function validateRegisterFields(username: string, email: string, password: string): AuthFieldErrors {
  const nextErrors: AuthFieldErrors = {}
  const usernameError = validateRegisterUsername(username)
  const emailError = validateRegisterEmail(email)
  const passwordError = validateRegisterPassword(password)

  if (usernameError) nextErrors.registerUsername = usernameError
  if (emailError) nextErrors.registerEmail = emailError
  if (passwordError) nextErrors.registerPassword = passwordError

  return nextErrors
}

function clearFieldError(
  field: AuthFieldKey,
  setFieldErrors: Dispatch<SetStateAction<AuthFieldErrors>>,
) {
  setFieldErrors((current) => {
    if (!current[field]) {
      return current
    }

    const nextErrors = { ...current }
    delete nextErrors[field]
    return nextErrors
  })
}

function setSingleFieldError(
  field: AuthFieldKey,
  message: string,
  setFieldErrors: Dispatch<SetStateAction<AuthFieldErrors>>,
) {
  setFieldErrors((current) => {
    if (!message) {
      if (!current[field]) {
        return current
      }
      const nextErrors = { ...current }
      delete nextErrors[field]
      return nextErrors
    }

    return { ...current, [field]: message }
  })
}

function mapAuthErrorToFieldErrors(mode: 'login' | 'register', message: string): AuthFieldErrors {
  const normalized = message.trim().toLowerCase()

  if (mode === 'login') {
    if (normalized.includes('account cannot be blank')) {
      return { account: '请填写邮箱或用户名' }
    }
    if (normalized.includes('password cannot be blank')) {
      return { loginPassword: '请填写密码' }
    }
    if (normalized.includes('account or password') || normalized.includes('账号') || normalized.includes('密码')) {
      return { loginPassword: '账号或密码填写有误' }
    }
    return { loginPassword: '登录失败，请重试' }
  }

  if (normalized.includes('username cannot be blank')) {
    return { registerUsername: '请填写用户名' }
  }
  if (normalized.includes('username length')) {
    return { registerUsername: '用户名长度应为 3-32 位' }
  }
  if (normalized.includes('username') && normalized.includes('exist')) {
    return { registerUsername: '用户名已存在' }
  }
  if (normalized.includes('email cannot be blank')) {
    return { registerEmail: '请填写邮箱' }
  }
  if (normalized.includes('email format')) {
    return { registerEmail: '请填写正确邮箱，如 xxx@xx.com' }
  }
  if (normalized.includes('email') && normalized.includes('exist')) {
    return { registerEmail: '邮箱已存在' }
  }
  if (normalized.includes('password cannot be blank')) {
    return { registerPassword: '请填写密码' }
  }
  if (normalized.includes('password length')) {
    return { registerPassword: '密码长度应为 6-64 位' }
  }

  return { registerEmail: '注册失败，请重试' }
}
