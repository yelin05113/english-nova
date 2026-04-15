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
    <div className="min-h-screen grid place-items-center px-5 py-10">
      {/* 品牌 */}
      <div className="w-full max-w-[860px]">
        <div className="flex items-center gap-[14px] mb-7 justify-center">
          <span className="w-[58px] h-[58px] grid place-items-center rounded-[20px] bg-gradient-to-br from-[#365946] to-[#7f9f78] text-[#faf4ea] font-bold tracking-[0.16em]">
            1103
          </span>
          <div>
            <p className="m-0 text-muted uppercase tracking-[0.18em] text-[0.72rem]">English Nova</p>
            <h1>1103 单词控制台</h1>
          </div>
        </div>

        <section className="grid grid-cols-[400px_minmax(0,1fr)] gap-[18px] max-[1120px]:grid-cols-1">
          <article className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[30px] p-[22px]">
            {/* Tab row */}
            <div className="flex gap-4 px-1.5 py-1.5 rounded-full bg-[rgba(226,234,224,0.78)] w-fit mb-4">
              <button
                type="button"
                className={`border-none rounded-full px-[18px] py-3 cursor-pointer text-forest-deep transition-[background] duration-[160ms] ${authTab === 'login' ? 'bg-[rgba(255,252,247,0.92)]' : 'bg-transparent'}`}
                onClick={() => setAuthTab('login')}
              >
                登录
              </button>
              <button
                type="button"
                className={`border-none rounded-full px-[18px] py-3 cursor-pointer text-forest-deep transition-[background] duration-[160ms] ${authTab === 'register' ? 'bg-[rgba(255,252,247,0.92)]' : 'bg-transparent'}`}
                onClick={() => setAuthTab('register')}
              >
                注册
              </button>
            </div>

            {error && (
              <p className="mb-4 px-[14px] py-3 rounded-2xl bg-[rgba(207,118,94,0.16)] text-[#8b3e30]">
                {error}
              </p>
            )}

            {authTab === 'login' ? (
              <div className="grid gap-[14px]">
                <label className="grid gap-2">
                  <span className="text-forest-deep">用户名或邮箱</span>
                  <input
                    className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
                    value={account}
                    onChange={(e) => setAccount(e.target.value)}
                  />
                </label>
                <label className="grid gap-2">
                  <span className="text-forest-deep">密码</span>
                  <input
                    className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
                    type="password"
                    value={loginPassword}
                    onChange={(e) => setLoginPassword(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && void handleLogin()}
                  />
                </label>
                <button
                  type="button"
                  className="border-none bg-gradient-to-br from-[#385d4b] to-[#6f8c67] text-[#faf4ea] rounded-2xl px-4 py-[13px] cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5"
                  onClick={() => void handleLogin()}
                >
                  登录并进入词库
                </button>
              </div>
            ) : (
              <div className="grid gap-[14px]">
                <label className="grid gap-2">
                  <span className="text-forest-deep">用户名</span>
                  <input
                    className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
                    value={registerUsername}
                    onChange={(e) => setRegisterUsername(e.target.value)}
                  />
                </label>
                <label className="grid gap-2">
                  <span className="text-forest-deep">邮箱</span>
                  <input
                    className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
                    type="email"
                    value={registerEmail}
                    onChange={(e) => setRegisterEmail(e.target.value)}
                  />
                </label>
                <label className="grid gap-2">
                  <span className="text-forest-deep">密码</span>
                  <input
                    className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
                    type="password"
                    value={registerPassword}
                    onChange={(e) => setRegisterPassword(e.target.value)}
                  />
                </label>
                <button
                  type="button"
                  className="border-none bg-gradient-to-br from-[#385d4b] to-[#6f8c67] text-[#faf4ea] rounded-2xl px-4 py-[13px] cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5"
                  onClick={() => void handleRegister()}
                >
                  注册并开始使用
                </button>
              </div>
            )}
          </article>

          {overview && (
            <article className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[30px] p-[22px] grid gap-[14px] content-start">
              <p className="m-0 text-muted uppercase tracking-[0.18em] text-[0.72rem]">平台功能</p>
              <h2 className="m-0 text-[1.8rem] text-forest-deep leading-[1.1]">{overview.productName}</h2>
              <p>{overview.theme}</p>
              <div className="flex flex-wrap gap-2">
                {overview.supportedPlatforms.map((p) => (
                  <span key={p} className="px-3 py-2 rounded-full bg-[rgba(226,234,224,0.88)] text-muted">
                    {p}
                  </span>
                ))}
              </div>
            </article>
          )}
        </section>
      </div>
    </div>
  )
}
