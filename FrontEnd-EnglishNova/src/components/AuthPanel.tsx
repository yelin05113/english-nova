import type { SystemOverview } from '../types/types'

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
              />
            </label>
            <button
              type="button"
              className="border-none bg-gradient-to-br from-[#385d4b] to-[#6f8c67] text-[#faf4ea] rounded-2xl px-4 py-[13px] cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5"
              onClick={() => void onLogin()}
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
              onClick={() => void onRegister()}
            >
              注册并创建私有空间
            </button>
          </div>
        )}
      </article>
      <article className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[30px] p-[22px]">
        <p className="m-0 text-muted uppercase tracking-[0.18em] text-[0.72rem] mb-3">系统模块</p>
        <div className="grid gap-[14px]">
          {(overview?.modules ?? []).map((item) => (
            <div key={item.name} className="grid gap-2">
              <strong className="text-forest-deep">{item.name}</strong>
              <span>{item.responsibility}</span>
            </div>
          ))}
        </div>
      </article>
    </section>
  )
}
