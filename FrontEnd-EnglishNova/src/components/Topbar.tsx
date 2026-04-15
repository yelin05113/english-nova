import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router'
import { navItems } from '../constants'
import { useAppStateContext } from '../context/AppStateContext'

export function Topbar() {
  const { user, clearAuth, loading } = useAppStateContext()
  const { pathname } = useLocation()
  const navRef = useRef<HTMLDivElement>(null)
  const [pill, setPill] = useState<{ left: number; width: number; top: number; height: number; opacity: number }>({
    left: 0,
    width: 0,
    top: 0,
    height: 0,
    opacity: 0,
  })

  useEffect(() => {
    const nav = navRef.current
    if (!nav) return
    const active = nav.querySelector('a[aria-current="page"]') as HTMLElement | null
    if (!active) {
      setPill((p) => ({ ...p, opacity: 0 }))
      return
    }
    const navRect = nav.getBoundingClientRect()
    const activeRect = active.getBoundingClientRect()
    setPill({
      left: activeRect.left - navRect.left,
      width: activeRect.width,
      top: activeRect.top - navRect.top,
      height: activeRect.height,
      opacity: 1,
    })
  }, [pathname])

  return (
    <header className="flex items-center justify-between gap-4 px-[22px] py-[14px] sticky top-3 z-20 border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[30px] backdrop-blur-sm">
      <div className="flex items-center gap-4">
        <span className="w-[58px] h-[58px] grid place-items-center rounded-[20px] bg-gradient-to-br from-[#365946] to-[#7f9f78] text-[#faf4ea] font-bold tracking-[0.16em]">
          1103
        </span>
        <div>
          <p className="m-0 text-muted uppercase tracking-[0.18em] text-[0.72rem]">English Nova</p>
          <h1>1103 单词控制台</h1>
        </div>
      </div>

      <nav ref={navRef} className="relative flex max-[860px]:hidden gap-1 items-center bg-[rgba(226,234,224,0.72)] rounded-full px-[5px] py-[5px]">
        {/* Sliding active pill */}
        <div
          className="absolute rounded-full bg-[rgba(255,252,247,0.95)] shadow-[0_2px_10px_rgba(70,92,72,0.12)] pointer-events-none transition-all duration-300 ease-[cubic-bezier(0.25,0.8,0.25,1)]"
          style={{
            left: pill.left,
            width: pill.width,
            top: pill.top,
            height: pill.height,
            opacity: pill.opacity,
          }}
        />
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.path === '/'}
            className="relative z-10 block px-4 py-[9px] rounded-full text-[0.88rem] text-forest-deep no-underline transition-colors duration-200 whitespace-nowrap hover:text-forest-deep"
          >
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="flex items-center gap-2.5">
        {loading && (
          <span className="px-3 py-2 rounded-full bg-[rgba(226,234,224,0.88)] text-muted">
            同步中
          </span>
        )}
        {user && (
          <>
            <span className="px-[14px] py-[7px] rounded-full bg-[rgba(226,234,224,0.88)] text-forest-deep text-[0.84rem] font-medium">
              {user.username}
            </span>
            <button
              type="button"
              className="border border-[rgba(88,112,90,0.16)] bg-[rgba(255,252,247,0.88)] text-ink-soft rounded-2xl px-4 py-[13px] cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5"
              onClick={clearAuth}
            >
              退出
            </button>
          </>
        )}
      </div>
    </header>
  )
}
