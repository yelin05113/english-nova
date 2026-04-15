import { useEffect, useRef, useState } from 'react'
import { useAppStateContext } from '../context/AppStateContext'

function AnimatedNumber({ value, duration = 1200 }: { value: number; duration?: number }) {
  const [display, setDisplay] = useState(0)
  const startRef = useRef<number | null>(null)
  const fromRef = useRef(0)
  const toRef = useRef(value)

  useEffect(() => {
    fromRef.current = display
    toRef.current = value
    startRef.current = null
    let raf = 0

    const step = (ts: number) => {
      if (startRef.current === null) startRef.current = ts
      const p = Math.min(1, (ts - startRef.current) / duration)
      const eased = 1 - Math.pow(1 - p, 4)
      setDisplay(Math.floor(fromRef.current + (toRef.current - fromRef.current) * eased))
      if (p < 1) raf = requestAnimationFrame(step)
    }

    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [value, duration])

  return <span>{display}</span>
}

function RingChart({
  percent,
  size = 96,
  stroke = 10,
  color = '#64815f',
  bg = 'rgba(82,107,84,0.12)',
  children,
}: {
  percent: number
  size?: number
  stroke?: number
  color?: string
  bg?: string
  children?: React.ReactNode
}) {
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const dash = (percent / 100) * c
  const [offset, setOffset] = useState(c)

  useEffect(() => {
    const t = setTimeout(() => setOffset(c - dash), 80)
    return () => clearTimeout(t)
  }, [dash, c])

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={bg} strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={offset}
          style={{ transition: 'stroke-dashoffset 1.2s cubic-bezier(0.22, 0.61, 0.36, 1)' }}
        />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">{children}</div>
    </div>
  )
}

export function ProgressView() {
  const { progress, agenda } = useAppStateContext()
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => setMounted(true), 50)
    return () => clearTimeout(t)
  }, [])

  const accuracy = progress?.accuracyRate ?? 0
  const totalWords = progress?.totalWords ?? 0
  const clearedWords = progress?.clearedWords ?? 0
  const inProgressWords = progress?.inProgressWords ?? 0
  const newWords = progress?.newWords ?? 0
  const wordbooks = progress?.wordbooks ?? 0
  const answered = progress?.answeredQuestions ?? 0

  const newCards = agenda?.newCards ?? 0
  const reviewCards = agenda?.reviewCards ?? 0
  const listeningCards = agenda?.listeningCards ?? 0
  const minutes = agenda?.estimatedMinutes ?? 0
  const focusAreas = agenda?.focusAreas ?? []

  const cardBase =
    'relative overflow-hidden rounded-[22px] border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] backdrop-blur-sm transition-all duration-500'

  return (
    <div className="w-full">
      {/* Top: Accuracy hero */}
      <div
        className={`mb-6 grid gap-6 lg:grid-cols-3 ${mounted ? 'translate-y-0 opacity-100' : 'translate-y-6 opacity-0'} transition-all duration-700 delay-100`}
      >
        <div className={`${cardBase} col-span-1 lg:col-span-2 p-7 hover:shadow-[0_28px_60px_rgba(70,92,72,0.12)]`}>
          <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex-1">
              <div className="text-xs font-semibold uppercase tracking-widest text-muted">总体正确率</div>
              <div className="mt-2 text-[clamp(2.4rem,5vw,3.6rem)] font-semibold leading-none text-forest-deep">
                <AnimatedNumber value={Math.round(accuracy)} />%
              </div>
              <div className="mt-3 max-w-md text-ink-soft">
                你已答了 <span className="font-semibold text-forest-deep"><AnimatedNumber value={answered} /></span> 道题，
                其中 <span className="font-semibold text-forest-deep"><AnimatedNumber value={progress?.correctAnswers ?? 0} /></span> 道正确。
                保持节奏，稳定积累。
              </div>
            </div>
            <div className="flex items-center justify-center">
              <RingChart percent={accuracy} size={130} stroke={12} color="#64815f">
                <div className="text-center">
                  <div className="text-[1.1rem] font-semibold text-forest-deep">{Math.round(accuracy)}%</div>
                  <div className="text-[0.7rem] text-muted">Accuracy</div>
                </div>
              </RingChart>
            </div>
          </div>
          {/* Decorative leaf-like gradient blob */}
          <div
            className="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rounded-full opacity-40 blur-3xl"
            style={{ background: 'radial-gradient(circle at center, rgba(132,165,126,0.35), transparent 70%)' }}
          />
        </div>

        <div className={`${cardBase} p-7 hover:shadow-[0_28px_60px_rgba(70,92,72,0.12)]`}>
          <div className="text-xs font-semibold uppercase tracking-widest text-muted">预计学习时间</div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-[clamp(2rem,4vw,2.8rem)] font-semibold leading-none text-forest-deep">
              <AnimatedNumber value={minutes} />
            </span>
            <span className="text-muted">分钟</span>
          </div>
          <div className="mt-4 flex gap-3">
            <span className="inline-flex items-center rounded-full border border-[rgba(82,107,84,0.18)] bg-[rgba(255,255,255,0.55)] px-3 py-1 text-sm text-ink-soft">
              新词 {newCards}
            </span>
            <span className="inline-flex items-center rounded-full border border-[rgba(82,107,84,0.18)] bg-[rgba(255,255,255,0.55)] px-3 py-1 text-sm text-ink-soft">
              复习 {reviewCards}
            </span>
          </div>
        </div>
      </div>

      {/* Stats grid */}
      <div
        className={`mb-6 grid grid-cols-2 gap-4 md:grid-cols-4 ${mounted ? 'translate-y-0 opacity-100' : 'translate-y-6 opacity-0'} transition-all duration-700 delay-200`}
      >
        {[
          { label: '总词数', value: totalWords, accent: '#64815f' },
          { label: '已掌握', value: clearedWords, accent: '#2e4634' },
          { label: '学习中', value: inProgressWords, accent: '#8faeb0' },
          { label: '单词本', value: wordbooks, accent: '#5a6759' },
        ].map((s, i) => (
          <div
            key={s.label}
            className={`${cardBase} p-5 transition-all hover:-translate-y-1 hover:shadow-[0_28px_60px_rgba(70,92,72,0.12)]`}
            style={{ transitionDelay: `${i * 60}ms` }}
          >
            <div className="text-xs font-semibold uppercase tracking-widest text-muted">{s.label}</div>
            <div className="mt-2 text-[clamp(1.6rem,3vw,2rem)] font-semibold leading-none" style={{ color: s.accent }}>
              <AnimatedNumber value={s.value} />
            </div>
            <div
              className="pointer-events-none absolute -bottom-6 -right-6 h-24 w-24 rounded-full opacity-30 blur-2xl"
              style={{ background: `radial-gradient(circle at center, ${s.accent}30, transparent 70%)` }}
            />
          </div>
        ))}
      </div>

      {/* Bottom: Focus areas + New words mini card */}
      <div
        className={`grid gap-6 lg:grid-cols-3 ${mounted ? 'translate-y-0 opacity-100' : 'translate-y-6 opacity-0'} transition-all duration-700 delay-300`}
      >
        <div className={`${cardBase} col-span-1 lg:col-span-2 p-7 hover:shadow-[0_28px_60px_rgba(70,92,72,0.12)]`}>
          <div className="mb-4 flex items-center justify-between">
            <div className="text-xs font-semibold uppercase tracking-widest text-muted">当前建议</div>
            <div className="h-1.5 w-1.5 rounded-full bg-fern animate-pulse" />
          </div>
          {focusAreas.length === 0 ? (
            <p className="text-muted">暂无建议，继续学习以获取个性化反馈。</p>
          ) : (
            <div className="flex flex-wrap gap-3">
              {focusAreas.map((item, idx) => (
                <div
                  key={item}
                  className="group relative overflow-hidden rounded-2xl border border-[rgba(82,107,84,0.14)] bg-[rgba(255,255,255,0.55)] px-4 py-3 transition-all hover:-translate-y-0.5 hover:border-[rgba(82,107,84,0.28)] hover:shadow-sm"
                  style={{ animationDelay: `${idx * 80}ms` }}
                >
                  <span className="relative z-10 text-forest-deep">{item}</span>
                  <div className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/60 to-transparent transition-transform duration-700 group-hover:translate-x-full" />
                </div>
              ))}
            </div>
          )}
        </div>

        <div className={`${cardBase} p-7 hover:shadow-[0_28px_60px_rgba(70,92,72,0.12)]`}>
          <div className="text-xs font-semibold uppercase tracking-widest text-muted">待学新词</div>
          <div className="mt-2 text-[clamp(1.8rem,3.5vw,2.4rem)] font-semibold leading-none text-forest-deep">
            <AnimatedNumber value={newWords} />
          </div>
          <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-[rgba(82,107,84,0.12)]">
            <div
              className="h-full rounded-full bg-gradient-to-r from-fern to-forest-deep transition-all duration-1000"
              style={{ width: mounted ? `${Math.min(100, Math.max(5, (newWords / Math.max(1, totalWords)) * 100))}%` : '0%' }}
            />
          </div>
          <div className="mt-2 text-xs text-muted">占词库 {totalWords > 0 ? Math.round((newWords / totalWords) * 100) : 0}%</div>
        </div>
      </div>
    </div>
  )
}
