import { useMemo, useState } from 'react'
import type { PublicWordbook } from '../api/modules/search'
import { useAppStateContext } from '../context/AppStateContext'

export function LibraryView() {
  const {
    wordbooks,
    publicWordbooks,
    selectedPublicWordbookId,
    setSelectedPublicWordbookId,
    selectedPublicWordbook,
    subscribingPublicWordbookId,
    unsubscribingPublicWordbookId,
    resettingPublicWordbookId,
    handleSubscribePublicWordbook,
    handleUnsubscribePublicWordbook,
    handleResetPublicWordbookProgress,
    selectedWordbookId,
    setSelectedWordbookId,
    creatingQuiz,
    handleCreateQuiz,
  } = useAppStateContext()

  const [unsubscribeMode, setUnsubscribeMode] = useState(false)
  const [unsubscribeTarget, setUnsubscribeTarget] = useState<PublicWordbook | null>(null)

  const subscribedPublicWordbooks = useMemo(
    () => publicWordbooks.filter((book) => book.subscribed),
    [publicWordbooks],
  )

  function onResetPublicWordbook() {
    if (!selectedPublicWordbookId) return
    if (!window.confirm('确定要重置这个公共词书的进度，并清空错词记录吗？')) {
      return
    }
    void handleResetPublicWordbookProgress(selectedPublicWordbookId)
  }

  function requestUnsubscribe(book: PublicWordbook) {
    setSelectedPublicWordbookId(book.id)
    setUnsubscribeTarget(book)
  }

  async function confirmUnsubscribe() {
    if (!unsubscribeTarget) return
    const targetId = unsubscribeTarget.id
    await handleUnsubscribePublicWordbook(targetId)
    setUnsubscribeTarget(null)
    setUnsubscribeMode(false)
  }

  function selectSubscribedWordbook(book: PublicWordbook) {
    setSelectedPublicWordbookId(book.id)
  }

  return (
    <>
      <div className="pixel-library">
        <section className="pixel-library-hero" aria-labelledby="library-title">
          <div>
            <p className="eyebrow">词书仓库</p>
            <h2 id="library-title">像素词库</h2>
          </div>
          <div className="pixel-library-scene" aria-hidden="true">
            <span className="pixel-shelf pixel-shelf-top" />
            <span className="pixel-book pixel-book-a" />
            <span className="pixel-book pixel-book-b" />
            <span className="pixel-book pixel-book-c" />
            <span className="pixel-shelf pixel-shelf-bottom" />
            <span className="pixel-spark pixel-spark-a" />
            <span className="pixel-spark pixel-spark-b" />
          </div>
        </section>

        <div className="pixel-library-grid single">
          <div className="list pixel-panel pixel-panel-menu">
            <div className="toolbar pixel-toolbar">
            <div>
              <p className="eyebrow">我的导入词书</p>
              <h4>共 {wordbooks.length} 本</h4>
            </div>
          </div>
          {wordbooks.length > 0 ? (
            wordbooks.map((book) => (
              <button
                key={book.id}
                type="button"
                className={book.id === selectedWordbookId ? 'book active' : 'book'}
                onClick={() => setSelectedWordbookId(book.id)}
              >
                <strong>{book.name}</strong>
                <span>
                  {book.wordCount} 词 / 已斩 {book.clearedCount}
                </span>
              </button>
            ))
          ) : (
            <div className="meta">
              <span className="meta-label">导入</span>
              <span className="meta-value">还没有导入词书。</span>
            </div>
          )}

          <div className="public-wordbooks pixel-section">
            <div className="toolbar public-wordbooks-head pixel-toolbar">
              <div>
                <p className="eyebrow">已订阅公共词书</p>
                <h4>已订阅 {subscribedPublicWordbooks.length} 本</h4>
              </div>
              {subscribedPublicWordbooks.length > 0 && (
                <button
                  type="button"
                  className={unsubscribeMode ? 'ghost danger active' : 'ghost danger'}
                  onClick={() => setUnsubscribeMode((current) => !current)}
                >
                  {unsubscribeMode ? '取消删除' : '删除订阅'}
                </button>
              )}
            </div>
            {subscribedPublicWordbooks.length > 0 ? (
              <div className="public-wordbook-grid">
                {subscribedPublicWordbooks.map((book) => (
                  <div key={book.id} className="public-subscription-item">
                    <div className="public-subscription-row">
                      <button
                        type="button"
                        className={book.id === selectedPublicWordbookId ? 'book active' : 'book'}
                        onClick={() => selectSubscribedWordbook(book)}
                      >
                        <strong>{book.name}</strong>
                        <span>
                          已斩 {book.completedCount}/{book.wordCount} / 错词 {book.wrongCount}
                        </span>
                      </button>
                      {unsubscribeMode && (
                        <button
                          type="button"
                          className="unsubscribe-x"
                          aria-label={`取消订阅 ${book.name}`}
                          disabled={unsubscribingPublicWordbookId === book.id}
                          onClick={() => requestUnsubscribe(book)}
                        >
                          ×
                        </button>
                      )}
                    </div>
                    {book.id === selectedPublicWordbookId && (
                      <div className="subscribed-action-card" role="region" aria-label={`${book.name} 学习操作`}>
                        <div className="subscribed-action-buttons">
                          <button
                            type="button"
                            className="primary"
                            onClick={() => void handleCreateQuiz('PUBLIC_WORDBOOK')}
                            disabled={creatingQuiz}
                          >
                            {creatingQuiz ? '创建中...' : '继续学习'}
                          </button>
                          <button
                            type="button"
                            className="ghost"
                            onClick={onResetPublicWordbook}
                            disabled={resettingPublicWordbookId === book.id}
                          >
                            {resettingPublicWordbookId === book.id ? '重置中...' : '重置进度'}
                          </button>
                        </div>
                        <div className="subscribed-stat-box">
                          <span>总计：{book.wordCount}</span>
                          <span>已斩：{book.completedCount}</span>
                          <span>错词：{book.wrongCount}</span>
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div className="meta">
                <span className="meta-label">订阅</span>
                <span className="meta-value">订阅公共词书后，可以从保存的进度继续学习。</span>
              </div>
            )}

            <div className="toolbar public-wordbooks-head pixel-toolbar">
              <div>
                <p className="eyebrow">公共词书目录</p>
                <h4>{selectedPublicWordbook?.name ?? 'ECDICT'}</h4>
              </div>
              <button
                type="button"
                className="ghost"
                disabled={
                  !selectedPublicWordbookId ||
                  !!selectedPublicWordbook?.subscribed ||
                  subscribingPublicWordbookId != null
                }
                onClick={() => void handleSubscribePublicWordbook()}
              >
                {selectedPublicWordbook?.subscribed
                  ? '已订阅'
                  : subscribingPublicWordbookId === selectedPublicWordbookId
                    ? '订阅中...'
                    : '订阅'}
              </button>
            </div>
            <div className="public-wordbook-grid">
              {publicWordbooks.map((book) => (
                <button
                  key={book.id}
                  type="button"
                  className={book.id === selectedPublicWordbookId ? 'book active' : 'book'}
                  onClick={() => setSelectedPublicWordbookId(book.id)}
                >
                  <strong>{book.name}</strong>
                  <span>
                    {book.wordCount} 词 / {book.subscribed ? '已订阅' : book.licenseName}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
      </div>

      {unsubscribeTarget && (
        <div className="modal-backdrop unsubscribe-backdrop" role="presentation">
          <section className="modal-card unsubscribe-modal" role="dialog" aria-modal="true" aria-labelledby="unsubscribe-title">
            <div className="panel-head">
              <div>
                <p className="eyebrow">取消订阅</p>
                <h3 id="unsubscribe-title">是否确认取消订阅？</h3>
              </div>
            </div>
            <div className="meta">
              <span className="meta-label">词书</span>
              <span className="meta-value">{unsubscribeTarget.name}</span>
            </div>
            <div className="unsubscribe-actions">
              <button
                type="button"
                className="primary danger"
                disabled={unsubscribingPublicWordbookId === unsubscribeTarget.id}
                onClick={() => void confirmUnsubscribe()}
              >
                {unsubscribingPublicWordbookId === unsubscribeTarget.id ? '取消中...' : '是'}
              </button>
              <button
                type="button"
                className="ghost"
                disabled={unsubscribingPublicWordbookId === unsubscribeTarget.id}
                onClick={() => setUnsubscribeTarget(null)}
              >
                否
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  )
}
