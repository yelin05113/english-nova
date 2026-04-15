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
    <div className="home-layout">
      {/* 左侧：问候 + 统计 */}
      <section className="home-hero">
        <p className="eyebrow">个人导入 / 私有隔离 / 四选一斩词</p>
        <h2 className="home-greeting">
          {getGreeting()}，{user?.username ?? '背词者'}。
        </h2>
        <p className="home-subline">
          {totalTasks > 0
            ? `今天要背 ${newCards} 个新词，复习 ${reviewCards} 个单词，加油！`
            : '今日任务已完成，继续保持！'}
        </p>

        <div className="home-stats">
          <div className="home-stat-card">
            <span className="home-stat-label">新词</span>
            <strong className="home-stat-value">{newCards}</strong>
          </div>
          <div className="home-stat-card">
            <span className="home-stat-label">复习</span>
            <strong className="home-stat-value">{reviewCards}</strong>
          </div>
          <div className="home-stat-card">
            <span className="home-stat-label">正确率</span>
            <strong className="home-stat-value">{accuracy}%</strong>
          </div>
        </div>

        <div className="home-ctas">
          <button
            type="button"
            className="primary home-cta-btn"
            onClick={() => void handleCreateQuiz()}
            disabled={wordbooks.length === 0}
          >
            立即背词 →
          </button>
          <Link to="/imports" className="ghost home-cta-btn">
            导入词书
          </Link>
        </div>

        {/* 记忆完成率 */}
        <div className="home-completion">
          <div
            className="home-completion-ring"
            style={{ '--rate': `${completionRate}` } as React.CSSProperties}
          >
            <span className="home-completion-pct">{completionRate}%</span>
          </div>
          <div>
            <p className="home-completion-label">记忆完成率</p>
            <p className="home-completion-sub">已斩 {clearedWords} / 共 {totalWords} 词</p>
          </div>
        </div>
      </section>

      {/* 右侧：最近词书 */}
      <section className="home-books">
        <div className="home-books-head">
          <h3>最近在背单词</h3>
          <Link to="/library" className="home-books-link">全部词书 →</Link>
        </div>
        {wordbooks.length === 0 ? (
          <div className="home-empty">
            <p>还没有词书，先去导入吧</p>
            <Link to="/imports" className="primary">去导入</Link>
          </div>
        ) : (
          <div className="home-books-grid">
            {wordbooks.slice(0, 4).map((book) => {
              const bookRate = book.wordCount > 0
                ? Math.round((book.clearedCount / book.wordCount) * 100)
                : 0
              return (
                <div key={book.id} className="home-book-card">
                  <div className="home-book-cover">
                    <span>{book.name.charAt(0).toUpperCase()}</span>
                  </div>
                  <div className="home-book-info">
                    <strong>{book.name}</strong>
                    <span>{book.wordCount} 词 · 已斩 {book.clearedCount}</span>
                    <div className="home-book-bar-wrap">
                      <div
                        className="home-book-bar"
                        style={{ width: `${bookRate}%` }}
                      />
                    </div>
                  </div>
                  <span className="home-book-pct">{bookRate}%</span>
                </div>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
