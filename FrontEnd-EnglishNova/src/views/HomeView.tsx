import { useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import { Link } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'
import copywritingRaw from '../copywriting.txt?raw'
import { getWordbookArtwork } from '../utils/wordbookArtwork'

const fallbackQuotes = [
  '\u4eca\u5929\u4e5f\u7a33\u7a33\u5411\u524d\uff0c\u522b\u505c\u3002',
  '\u4f60\u80cc\u4e0b\u7684\u6bcf\u4e00\u4e2a\u8bcd\uff0c\u90fd\u4f1a\u5728\u672a\u6765\u66ff\u4f60\u8bf4\u8bdd\u3002',
  '\u6162\u4e00\u70b9\u6ca1\u5173\u7cfb\uff0c\u91cd\u8981\u7684\u662f\u4e00\u76f4\u5728\u5f80\u524d\u8d70\u3002',
  '\u575a\u6301\u4e0d\u662f\u786c\u6491\uff0c\u662f\u4f60\u77e5\u9053\u81ea\u5df1\u6b63\u5728\u53d8\u5f3a\u3002',
]

function getGreeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '\u51cc\u6668\u597d'
  if (hour < 12) return '\u65e9\u5b89'
  if (hour < 14) return '\u5348\u5b89'
  if (hour < 18) return '\u4e0b\u5348\u597d'
  return '\u665a\u4e0a\u597d'
}

function HomeBookCover({ name }: { name: string }) {
  const src = getWordbookArtwork(name, 'imported')
  const [imageError, setImageError] = useState(false)

  if (!src || imageError) {
    return (
      <div className="home-book-cover" aria-hidden="true">
        <span>{name.charAt(0).toUpperCase()}</span>
      </div>
    )
  }

  return (
    <div className="home-book-cover home-book-cover--artwork" aria-hidden="true">
      <img src={src} alt="" loading="lazy" onError={() => setImageError(true)} />
    </div>
  )
}

export function HomeView() {
  const { user, agenda, progress, wordbooks, handleCreateQuiz, openAuthModal } = useAppStateContext()

  const newCards = agenda?.newCards ?? 0
  const reviewCards = agenda?.reviewCards ?? 0
  const totalTasks = newCards + reviewCards
  const accuracy = progress?.accuracyRate ?? 0
  const clearedWords = progress?.clearedWords ?? 0
  const totalWords = progress?.totalWords ?? 0
  const completionRate = totalWords > 0 ? Math.round((clearedWords / totalWords) * 100) : 0

  const quote = useMemo(() => {
    const lines = copywritingRaw
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .filter((line) => !/[鍏-龥]/.test(line))

    if (lines.length === 0) {
      return fallbackQuotes[Math.floor(Math.random() * fallbackQuotes.length)] ?? fallbackQuotes[0]
    }

    return lines[Math.floor(Math.random() * lines.length)] ?? lines[0]
  }, [])

  const activeBooks = wordbooks.slice(0, 4)

  return (
    <div className="home-stage">
      <div className="home-layout">
        <section className="home-hero">
          <p className="eyebrow">{'\u4eca\u65e5\u4efb\u52a1 / \u516c\u5171\u8bcd\u4e66 / \u56db\u9009\u4e00\u7ec3\u4e60'}</p>
          <h2 className="home-greeting">{`${getGreeting()}，${user?.username ?? '\u8bbf\u5ba2'}。`}</h2>
          <p className="home-subline">
            {user
              ? totalTasks > 0
                ? `\u4eca\u5929\u8fd8\u6709 ${newCards} \u4e2a\u65b0\u8bcd\u3001${reviewCards} \u4e2a\u590d\u4e60\u8bcd\u3002`
                : '\u4eca\u65e5\u4efb\u52a1\u5df2\u7ecf\u5b8c\u6210\uff0c\u7ee7\u7eed\u4fdd\u6301\u5b66\u4e60\u8282\u594f\u3002'
              : '\u5148\u6d4f\u89c8\u516c\u5171\u8bcd\u4e66\uff0c\u60f3\u80cc\u8bcd\u6216\u6536\u85cf\u65f6\u518d\u968f\u65f6\u767b\u5f55\u3002'}
          </p>

          <div className="home-stats">
            <div className="home-stat-card">
              <span className="home-stat-label">{'\u65b0\u8bcd'}</span>
              <strong className="home-stat-value">{newCards}</strong>
            </div>
            <div className="home-stat-card">
              <span className="home-stat-label">{'\u590d\u4e60'}</span>
              <strong className="home-stat-value">{reviewCards}</strong>
            </div>
            <div className="home-stat-card">
              <span className="home-stat-label">{'\u6b63\u786e\u7387'}</span>
              <strong className="home-stat-value">{accuracy}%</strong>
            </div>
          </div>

          <div className="home-ctas">
            <button
              type="button"
              className="primary home-cta-btn"
              onClick={() => void handleCreateQuiz()}
              disabled={Boolean(user && wordbooks.length === 0)}
            >
              {'\u7acb\u5373\u80cc\u8bcd'}
            </button>
            {user ? (
              <Link to="/imports" className="ghost home-cta-btn">
                {'\u5bfc\u5165\u8bcd\u4e66'}
              </Link>
            ) : (
              <button type="button" className="ghost home-cta-btn" onClick={() => openAuthModal('register')}>
                {'\u6ce8\u518c\u540e\u5bfc\u5165'}
              </button>
            )}
          </div>

          <div className="home-completion">
            <div className="home-completion-ring" style={{ '--rate': `${completionRate}` } as CSSProperties}>
              <span className="home-completion-pct">{completionRate}%</span>
            </div>
            <div>
              <p className="home-completion-label">{'\u8bb0\u5fc6\u5b8c\u6210\u7387'}</p>
              <p className="home-completion-sub">
                {`\u5df2\u80cc ${clearedWords} / \u5171 ${totalWords} \u8bcd`}
              </p>
            </div>
          </div>
        </section>

        <section className="home-books">
          <div className="home-books-head">
            <h3>{user ? '\u6700\u8fd1\u5728\u80cc\u7684\u8bcd\u4e66' : '\u767b\u5f55\u540e\u53ef\u540c\u6b65\u4f60\u7684\u8bcd\u4e66'}</h3>
            <Link to="/library" className="home-books-link">
              {'\u67e5\u770b\u516c\u5171\u8bcd\u4e66'}
            </Link>
          </div>

          {activeBooks.length === 0 ? (
            <div className="home-empty">
              <p>
                {user
                  ? '\u8fd8\u6ca1\u6709\u8bcd\u4e66\uff0c\u5148\u53bb\u5bfc\u5165\u4e00\u672c\u3002'
                  : '\u5148\u901b\u901b\u516c\u5171\u8bcd\u4e66\uff0c\u6536\u85cf\u540e\u5c31\u80fd\u7ee7\u7eed\u80cc\u8bcd\u3002'}
              </p>
              {user ? (
                <Link to="/imports" className="primary">
                  {'\u53bb\u5bfc\u5165'}
                </Link>
              ) : (
                <button type="button" className="primary" onClick={() => openAuthModal()}>
                  {'\u767b\u5f55\u540e\u5f00\u59cb'}
                </button>
              )}
            </div>
          ) : (
            <div className="home-books-grid">
              {activeBooks.map((book) => {
                const bookRate = book.wordCount > 0 ? Math.round((book.clearedCount / book.wordCount) * 100) : 0

                return (
                  <div key={book.id} className="home-book-card">
                    <HomeBookCover name={book.name} />
                    <div className="home-book-info">
                      <strong>{book.name}</strong>
                      <span>{`${book.wordCount} \u8bcd / \u5df2\u80cc ${book.clearedCount}`}</span>
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

      <section className="home-quote-strip" aria-label={'\u52b1\u5fd7\u8bed\u5f55'}>
        <span className="home-quote-mark">{'\u4eca\u65e5\u4e00\u53e5'}</span>
        <p>{quote}</p>
      </section>
    </div>
  )
}
