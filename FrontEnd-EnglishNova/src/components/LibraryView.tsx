import { useAppStateContext } from '../context/AppStateContext'

const platformMeta: Record<string, { label: string; tone: string }> = {
  ANKI: { label: 'Anki', tone: 'from-[#365946] to-[#5a7a52]' },
  BAICIZHAN: { label: '百词斩', tone: 'from-[#4a6b4a] to-[#7a9f6e]' },
  BUBEIDANCI: { label: '不背单词', tone: 'from-[#5a7a5a] to-[#8aaf7e]' },
  SHANBAY: { label: '扇贝', tone: 'from-[#3d5c4a] to-[#6b8c64]' },
}

export function LibraryView() {
  const {
    wordbooks,
    selectedWordbookId,
    setSelectedWordbookId,
    selectedWordbook,
    wordbookProgress,
    entries,
    handleCreateQuiz,
  } = useAppStateContext()

  const onStartQuiz = () => void handleCreateQuiz()

  const completionRate =
    selectedWordbook && selectedWordbook.wordCount > 0
      ? Math.round((selectedWordbook.clearedCount / selectedWordbook.wordCount) * 100)
      : 0

  return (
    <div className="grid gap-6 animate-[fadeIn_500ms_ease-out]">
      {/* Main grid */}
      <div className="grid grid-cols-1 lg:grid-cols-[320px_minmax(0,1fr)] gap-5 items-start">
        {/* Left: Wordbook shelf */}
        <aside className="grid gap-3">
          {wordbooks.length === 0 ? (
            <div className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] rounded-[24px] p-6 text-center text-muted">
              <p className="m-0">暂无词书</p>
              <p className="m-0 mt-1 text-[0.84rem] opacity-80">先去导入一本吧</p>
            </div>
          ) : (
            wordbooks.map((book, idx) => {
              const isActive = book.id === selectedWordbookId
              const rate = book.wordCount > 0 ? Math.round((book.clearedCount / book.wordCount) * 100) : 0
              const meta = platformMeta[book.platform] ?? { label: book.platform, tone: 'from-[#5a7a5a] to-[#8aaf7e]' }
              return (
                <button
                  key={book.id}
                  type="button"
                  onClick={() => setSelectedWordbookId(book.id)}
                  className={`group relative w-full text-left rounded-[22px] p-[18px] cursor-pointer transition-all duration-200 overflow-hidden ${
                    isActive
                      ? 'bg-gradient-to-br from-[rgba(217,232,205,0.92)] to-[rgba(255,252,247,0.96)] shadow-[0_14px_36px_rgba(70,92,72,0.14)] border border-[rgba(79,105,82,0.24)] translate-x-1'
                      : 'bg-[rgba(252,248,241,0.78)] border border-[rgba(82,107,84,0.14)] hover:-translate-y-0.5 hover:shadow-[0_12px_30px_rgba(70,92,72,0.10)]'
                  }`}
                  style={{ animationDelay: `${idx * 40}ms` }}
                >
                  <div className="flex items-center gap-4">
                    <span className="shrink-0 w-9 h-9 rounded-[10px] bg-gradient-to-br text-[#faf4ea] text-[0.78rem] font-bold grid place-items-center shadow-inner tracking-wide"
                      style={{ backgroundImage: `linear-gradient(to bottom right, var(--tw-gradient-stops))` }}
                    >
                      <span className={`bg-gradient-to-br ${meta.tone} w-full h-full rounded-[10px] grid place-items-center`}>
                        {String(idx + 1).padStart(2, '0')}
                      </span>
                    </span>
                    <div className="flex-1 min-w-0 grid gap-0.5">
                      <strong className={`block truncate text-[0.96rem] tracking-[-0.01em] ${isActive ? 'text-forest-deep' : 'text-forest-deep'}`}>
                        {book.name}
                      </strong>
                      <span className="block text-[0.76rem] text-muted">
                        {meta.label} · {book.wordCount} 词
                      </span>
                    </div>
                    <span className={`shrink-0 text-[0.9rem] font-semibold ${rate === 100 ? 'text-fern' : 'text-ink-soft'}`}>
                      {rate}%
                    </span>
                  </div>

                  {/* Progress track */}
                  <div className="mt-3 h-1.5 rounded-full bg-[rgba(226,234,224,0.88)] overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-[width] duration-500 ${
                        rate === 100 ? 'bg-gradient-to-r from-[#385d4b] to-[#7aaf6e]' : 'bg-gradient-to-r from-[#5a7a5a] to-[#8aaf7e]'
                      }`}
                      style={{ width: `${rate}%` }}
                    />
                  </div>
                </button>
              )
            })
          )}
        </aside>

        {/* Right: Detail panel */}
        <section className="grid gap-5">
          {/* Stats poster */}
          <div className="relative rounded-[28px] border border-[rgba(82,107,84,0.14)] bg-gradient-to-br from-[rgba(252,248,241,0.96)] to-[rgba(226,234,224,0.88)] p-6 sm:p-8 overflow-hidden shadow-[0_20px_44px_rgba(70,92,72,0.08)]">
            {/* Decorative background ring */}
            <div className="pointer-events-none absolute -right-10 -top-10 w-64 h-64 rounded-full border-[1.5px] border-[rgba(82,107,84,0.08)]" />
            <div className="pointer-events-none absolute -right-4 -bottom-4 w-40 h-40 rounded-full border-[1.5px] border-[rgba(82,107,84,0.06)]" />

            {selectedWordbook ? (
              <div className="relative grid gap-6 sm:grid-cols-[auto_1fr] items-center">
                {/* Giant ring chart */}
                <div className="relative w-36 h-36 sm:w-44 sm:h-44 shrink-0 mx-auto sm:mx-0">
                  <div
                    className="w-full h-full rounded-full"
                    style={{
                      background: `conic-gradient(#4a7a5a calc(${completionRate} * 1%), rgba(226,234,224,0.72) 0)`,
                    }}
                  />
                  <div className="absolute inset-2 rounded-full bg-[rgba(252,248,241,0.96)] grid place-items-center">
                    <div className="text-center">
                      <span className="block text-[2rem] sm:text-[2.4rem] font-bold leading-none text-forest-deep tracking-[-0.04em]">
                        {completionRate}
                      </span>
                      <span className="block text-[0.72rem] text-muted uppercase tracking-widest mt-1">%</span>
                    </div>
                  </div>
                </div>

                {/* Stats typography */}
                <div className="grid gap-4 text-center sm:text-left">
                  <div>
                    <p className="m-0 text-muted uppercase tracking-[0.22em] text-[0.72rem]">当前词书</p>
                    <h3 className="m-0 mt-1 text-forest-deep text-[clamp(1.4rem,2.4vw,1.9rem)] leading-[1.15] tracking-[-0.02em]">
                      {selectedWordbook.name}
                    </h3>
                  </div>

                  <div className="flex items-center justify-center sm:justify-start gap-4 flex-wrap">
                    {[
                      { label: '总词数', value: wordbookProgress?.wordCount ?? selectedWordbook.wordCount },
                      { label: '已斩', value: wordbookProgress?.clearedCount ?? selectedWordbook.clearedCount },
                      { label: '答错中', value: wordbookProgress?.inProgressCount ?? 0 },
                    ].map((stat, i) => (
                      <div
                        key={stat.label}
                        className="min-w-[84px] rounded-[18px] border border-[rgba(82,107,84,0.12)] bg-[rgba(255,252,247,0.72)] px-4 py-3 text-center transition-transform duration-200 hover:-translate-y-0.5"
                        style={{ animationDelay: `${i * 60}ms` }}
                      >
                        <span className="block text-[1.3rem] font-semibold leading-none text-forest-deep">
                          {stat.value}
                        </span>
                        <span className="block text-[0.7rem] text-muted uppercase tracking-widest mt-1.5">
                          {stat.label}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Floating action on desktop */}
                <div className="sm:absolute sm:right-0 sm:top-0">
                  <button
                    type="button"
                    onClick={onStartQuiz}
                    className="w-full sm:w-auto inline-flex items-center justify-center gap-2 border-none bg-gradient-to-br from-[#2e4634] to-[#4a6b4a] text-[#faf4ea] rounded-2xl px-6 py-[14px] text-[0.92rem] font-medium cursor-pointer transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-[0_12px_28px_rgba(46,70,52,0.28)]"
                  >
                    开始斩词
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" className="opacity-90">
                      <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </button>
                </div>
              </div>
            ) : (
              <div className="grid place-items-center gap-3 py-10 text-center text-muted">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" className="opacity-60">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                <p className="m-0">选择一本词书查看详情</p>
              </div>
            )}
          </div>

          {/* Word preview cards */}
          {selectedWordbook && (
            <div>
              <div className="flex items-center justify-between gap-4 mb-4 px-1">
                <p className="m-0 text-muted uppercase tracking-[0.22em] text-[0.72rem]">Preview</p>
                <span className="text-[0.8rem] text-muted">前 {Math.min(entries.length, 12)} 个单词</span>
              </div>

              {entries.length === 0 ? (
                <div className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] rounded-[24px] p-8 text-center text-muted">
                  该词书暂无单词条目
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 max-h-[420px] overflow-y-auto pr-1 custom-scroll">
                  {entries.slice(0, 12).map((entry, idx) => (
                    <div
                      key={entry.id}
                      className="group relative rounded-[22px] border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] p-5 transition-all duration-200 hover:-translate-y-1 hover:shadow-[0_16px_40px_rgba(70,92,72,0.14)] hover:border-[rgba(79,105,82,0.24)]"
                      style={{
                        perspective: '800px',
                        animationDelay: `${idx * 30}ms`,
                      }}
                    >
                      {/* Top row: word + difficulty dots */}
                      <div className="flex items-start justify-between gap-3">
                        <strong className="text-forest-deep text-[1.15rem] tracking-[-0.01em] leading-tight">
                          {entry.word}
                        </strong>
                        <div className="flex gap-1 pt-1.5">
                          {Array.from({ length: 3 }).map((_, i) => (
                            <span
                              key={i}
                              className={`w-1.5 h-1.5 rounded-full ${
                                i < entry.difficulty ? 'bg-[#64815f]' : 'bg-[rgba(82,107,84,0.16)]'
                              }`}
                            />
                          ))}
                        </div>
                      </div>

                      {/* Phonetic */}
                      {entry.phonetic && (
                        <span className="block mt-1 text-[0.8rem] text-fern font-medium">{entry.phonetic}</span>
                      )}

                      {/* Meaning */}
                      <p className="mt-3 text-[0.92rem] text-ink-soft leading-relaxed line-clamp-2">
                        {entry.meaningCn}
                      </p>

                      {/* Example sentence */}
                      {entry.exampleSentence && (
                        <p className="mt-3 text-[0.8rem] text-muted italic leading-relaxed line-clamp-2 border-t border-[rgba(82,107,84,0.10)] pt-3">
                          “{entry.exampleSentence}”
                        </p>
                      )}

                      {/* Hover accent line */}
                      <div className="absolute left-0 top-6 bottom-6 w-1 rounded-r-full bg-gradient-to-b from-[#64815f] to-[#8aaf7e] opacity-0 group-hover:opacity-100 transition-opacity duration-200" />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </section>
      </div>

      {/* Inline keyframes */}
      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(8px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .line-clamp-2 {
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }
        .custom-scroll::-webkit-scrollbar {
          width: 6px;
        }
        .custom-scroll::-webkit-scrollbar-track {
          background: transparent;
        }
        .custom-scroll::-webkit-scrollbar-thumb {
          background: rgba(82, 107, 84, 0.22);
          border-radius: 9999px;
        }
        .custom-scroll::-webkit-scrollbar-thumb:hover {
          background: rgba(82, 107, 84, 0.35);
        }
      `}</style>
    </div>
  )
}
