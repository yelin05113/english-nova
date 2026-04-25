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
      <section className="home-hero">
        <p className="eyebrow">今日任务 / 私有词书 / 四选一练习</p>
        <h2 className="home-greeting">
          {getGreeting()}，{user?.username ?? '背词玩家'}。
        </h2>
        <p className="home-subline">
          {totalTasks > 0
            ? `今天还有 ${newCards} 个新词、${reviewCards} 个复习词。`
            : '今日任务已完成，保持节奏。'}
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
            立即背词
          </button>
          <Link to="/imports" className="ghost home-cta-btn">
            导入词书
          </Link>
        </div>

        <div className="home-completion">
          <div className="home-completion-ring" style={{ '--rate': `${completionRate}` } as React.CSSProperties}>
            <span className="home-completion-pct">{completionRate}%</span>
          </div>
          <div>
            <p className="home-completion-label">记忆完成率</p>
            <p className="home-completion-sub">
              已斩 {clearedWords} / 共 {totalWords} 词
            </p>
          </div>
        </div>
      </section>

      <section className="home-books">
        <div className="home-books-head">
          <h3>最近在背的词书</h3>
          <Link to="/library" className="home-books-link">
            全部词书
          </Link>
        </div>
        {wordbooks.length === 0 ? (
          <div className="home-empty">
            <p>还没有词书，先去导入一本。</p>
            <Link to="/imports" className="primary">
              去导入
            </Link>
          </div>
        ) : (
          <div className="home-books-grid">
            {wordbooks.slice(0, 4).map((book) => {
              const bookRate = book.wordCount > 0 ? Math.round((book.clearedCount / book.wordCount) * 100) : 0
              return (
                <div key={book.id} className="home-book-card">
                  <div className="home-book-cover">
                    <span>{book.name.charAt(0).toUpperCase()}</span>
                  </div>
                  <div className="home-book-info">
                    <strong>{book.name}</strong>
                    <span>
                      {book.wordCount} 词 / 已斩 {book.clearedCount}
                    </span>
                    <div className="home-book-bar-wrap">
                      <div className="home-book-bar" style={{ width: `${bookRate}%` }} />
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
