import { Link } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'

function getGreeting(): string {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早安'
  if (h < 14) return '午安'
  if (h < 18) return '下午好'
  return '晚上好'
}

export function HomeView() {
  const { user, agenda, progress, wordbooks, handleCreateQuiz } = useAppStateContext()

  const newCards = agenda?.newCards ?? 0
  const reviewCards = agenda?.reviewCards ?? 0
  const totalTasks = newCards + reviewCards
  const accuracy = progress?.accuracyRate ?? 0
  const clearedWords = progress?.clearedWords ?? 0
  const totalWords = progress?.totalWords ?? 0
  const completionRate = totalWords > 0 ? Math.round((clearedWords / totalWords) * 100) : 0

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] gap-5 items-start max-[860px]:grid-cols-1">
      {/* 左侧：问候 + 统计 */}
      <section className="border border-[rgba(82,107,84,0.14)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[30px] px-8 py-9 bg-[radial-gradient(circle_at_top_left,rgba(217,232,205,0.92),transparent_34%),linear-gradient(135deg,rgba(249,245,237,0.96),rgba(226,234,224,0.88))]">
        <p className="m-0 text-muted uppercase tracking-[0.18em] text-[0.72rem]">
          个人导入 / 私有隔离 / 四选一斩词
        </p>
        <h2 className="mt-2.5 mb-3 text-[clamp(2.4rem,4vw,3.8rem)] leading-[1.05] tracking-[-0.04em] text-forest-deep">
          {getGreeting()}，{user?.username ?? '背词者'}。
        </h2>
        <p className="text-ink-soft mb-7">
          {totalTasks > 0
            ? `今天要背 ${newCards} 个新词，复习 ${reviewCards} 个单词，加油！`
            : '今日任务已完成，继续保持！'}
        </p>

        <div className="flex gap-[14px] mb-6 flex-wrap">
          {[
            { label: '新词', value: newCards },
            { label: '复习', value: reviewCards },
            { label: '正确率', value: `${accuracy}%` },
          ].map(({ label, value }) => (
            <div
              key={label}
              className="border border-[rgba(82,107,84,0.14)] bg-[rgba(255,252,247,0.82)] rounded-[20px] px-5 py-4 min-w-[90px]"
            >
              <span className="block text-[0.76rem] tracking-[0.06em] text-muted uppercase">{label}</span>
              <strong className="block text-[2rem] font-bold leading-[1.1] text-forest-deep mt-1.5">
                {value}
              </strong>
            </div>
          ))}
        </div>

        <div className="flex gap-3 flex-wrap mb-8">
          <button
            type="button"
            className="border-none bg-gradient-to-br from-[#385d4b] to-[#6f8c67] text-[#faf4ea] rounded-2xl px-[22px] py-[13px] text-[0.95rem] cursor-pointer inline-flex items-center transition-[transform,box-shadow] duration-[160ms] hover:-translate-y-0.5 disabled:opacity-50 disabled:cursor-default disabled:translate-y-0"
            onClick={() => void handleCreateQuiz()}
            disabled={wordbooks.length === 0}
          >
            立即背词 →
          </button>
          <Link
            to="/imports"
            className="border border-[rgba(88,112,90,0.16)] bg-[rgba(255,252,247,0.88)] text-ink-soft rounded-2xl px-[22px] py-[13px] text-[0.95rem] cursor-pointer inline-flex items-center no-underline transition-[transform,box-shadow] duration-[160ms] hover:-translate-y-0.5"
          >
            导入词书
          </Link>
        </div>

        {/* 记忆完成率 */}
        <div className="flex items-center gap-4 p-[18px] rounded-[22px] bg-[rgba(226,234,224,0.72)] border border-[rgba(82,107,84,0.10)]">
          <div
            className="relative w-16 h-16 rounded-full grid place-items-center shrink-0"
            style={{
              background: `conic-gradient(#4a7a5a calc(${completionRate} * 1%), rgba(226,234,224,0.88) 0)`,
            }}
          >
            <span className="absolute z-10 text-[0.78rem] font-bold text-forest-deep">{completionRate}%</span>
            <div className="w-[46px] h-[46px] rounded-full bg-[rgba(249,245,237,0.96)]" />
          </div>
          <div>
            <p className="m-0 font-semibold text-forest-deep">记忆完成率</p>
            <p className="mt-1 text-[0.84rem] text-muted">已斩 {clearedWords} / 共 {totalWords} 词</p>
          </div>
        </div>
      </section>

      {/* 右侧：最近词书 */}
      <section className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[30px] p-7">
        <div className="flex items-center justify-between mb-[18px]">
          <h3 className="m-0 text-forest-deep">最近在背单词</h3>
          <Link to="/library" className="text-[0.84rem] text-fern no-underline hover:underline">
            全部词书 →
          </Link>
        </div>
        {wordbooks.length === 0 ? (
          <div className="grid place-items-center gap-4 py-10 px-5 text-center text-muted">
            <p>还没有词书，先去导入吧</p>
            <Link
              to="/imports"
              className="border-none bg-gradient-to-br from-[#385d4b] to-[#6f8c67] text-[#faf4ea] rounded-2xl px-4 py-[13px] no-underline inline-flex items-center cursor-pointer"
            >
              去导入
            </Link>
          </div>
        ) : (
          <div className="grid gap-3">
            {wordbooks.slice(0, 4).map((book) => {
              const bookRate = book.wordCount > 0
                ? Math.round((book.clearedCount / book.wordCount) * 100)
                : 0
              return (
                <div
                  key={book.id}
                  className="flex items-center gap-[14px] px-4 py-[14px] rounded-[18px] border border-[rgba(82,107,84,0.12)] bg-[rgba(250,247,241,0.9)] transition-[transform,background] duration-150 hover:-translate-y-px hover:bg-[rgba(255,252,247,0.95)]"
                >
                  <div className="w-11 h-11 rounded-[12px] bg-gradient-to-br from-[#385d4b] to-[#6f8c67] grid place-items-center shrink-0 text-[#faf4ea] text-[1.2rem] font-bold">
                    <span>{book.name.charAt(0).toUpperCase()}</span>
                  </div>
                  <div className="flex-1 min-w-0 grid gap-1">
                    <strong className="text-forest-deep text-[0.94rem] whitespace-nowrap overflow-hidden text-ellipsis">
                      {book.name}
                    </strong>
                    <span className="text-[0.78rem] text-muted">
                      {book.wordCount} 词 · 已斩 {book.clearedCount}
                    </span>
                    <div className="h-1 rounded-full bg-[rgba(226,234,224,0.88)] overflow-hidden mt-0.5">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-[#385d4b] to-[#7aaf6e] transition-[width] duration-[400ms]"
                        style={{ width: `${bookRate}%` }}
                      />
                    </div>
                  </div>
                  <span className="text-[0.8rem] font-semibold text-fern shrink-0">{bookRate}%</span>
                </div>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
