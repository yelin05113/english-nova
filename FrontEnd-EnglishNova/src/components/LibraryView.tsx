import { useMemo, useState } from 'react'
import type { PublicWordbook } from '../api/modules/search'
import { useAppStateContext } from '../context/AppStateContext'

function buildDailyTargetOptions(wordCount: number) {
  const cappedMax = Math.min(Math.max(wordCount, 0), 1000)
  if (cappedMax <= 0) {
    return []
  }

  const values = new Set<number>()
  const addRange = (start: number, end: number, step: number) => {
    for (let value = start; value <= end && value <= cappedMax; value += step) {
      values.add(value)
    }
  }

  addRange(10, 100, 10)
  addRange(125, 200, 25)
  addRange(250, 400, 50)
  addRange(500, 1000, 100)

  if (values.size === 0 || cappedMax < 10) {
    values.add(cappedMax)
  }
  values.add(cappedMax)

  return Array.from(values).sort((left, right) => left - right)
}

function getProgressPercent(book: PublicWordbook) {
  if (book.wordCount <= 0) {
    return 0
  }
  return Math.min(100, Math.max(0, (book.completedCount / book.wordCount) * 100))
}

function getEstimatedDays(book: PublicWordbook, targetCount = book.dailyTargetCount) {
  if (targetCount <= 0) {
    return null
  }
  const remainingWords = Math.max(0, book.wordCount - book.completedCount)
  if (remainingWords <= 0) {
    return 0
  }
  return Math.ceil(remainingWords / targetCount)
}

function isDailyQuotaCompleted(book: PublicWordbook) {
  return book.dailyTargetCount > 0 && book.todayCompletedCount >= book.dailyTargetCount
}

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
    handleUpdatePublicWordbookDailyTarget,
    selectedWordbookId,
    setSelectedWordbookId,
    creatingQuiz,
    handleCreateQuiz,
  } = useAppStateContext()

  const [unsubscribeMode, setUnsubscribeMode] = useState(false)
  const [unsubscribeTarget, setUnsubscribeTarget] = useState<PublicWordbook | null>(null)
  const [resetTarget, setResetTarget] = useState<PublicWordbook | null>(null)
  const [dailyTargetModalBookId, setDailyTargetModalBookId] = useState<number | null>(null)
  const [continueAfterTargetUpdateBookId, setContinueAfterTargetUpdateBookId] = useState<number | null>(null)
  const [savingDailyTarget, setSavingDailyTarget] = useState<number | null>(null)

  const subscribedPublicWordbooks = useMemo(
    () => publicWordbooks.filter((book) => book.subscribed),
    [publicWordbooks],
  )
  const dailyTargetModalBook =
    publicWordbooks.find((book) => book.id === dailyTargetModalBookId && book.subscribed) ?? null
  const dailyTargetOptions = useMemo(
    () => buildDailyTargetOptions(dailyTargetModalBook?.wordCount ?? 0),
    [dailyTargetModalBook?.wordCount],
  )
  const dailyTargetModalRequiresUpgrade =
    dailyTargetModalBook != null &&
    continueAfterTargetUpdateBookId === dailyTargetModalBook.id &&
    isDailyQuotaCompleted(dailyTargetModalBook)

  function onResetPublicWordbook() {
    if (!selectedPublicWordbookId) return
    if (!window.confirm('确定要重置这本公共词书的学习进度吗？')) {
      return
    }
    void handleResetPublicWordbookProgress(selectedPublicWordbookId)
  }

  void onResetPublicWordbook

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

  function requestReset(book: PublicWordbook) {
    setSelectedPublicWordbookId(book.id)
    setResetTarget(book)
  }

  async function confirmReset() {
    if (!resetTarget) return
    await handleResetPublicWordbookProgress(resetTarget.id)
    setResetTarget(null)
  }

  function selectSubscribedWordbook(book: PublicWordbook) {
    setSelectedPublicWordbookId(book.id)
  }

  function openDailyTargetModal(book: PublicWordbook, options?: { continueAfterUpdate?: boolean }) {
    setSelectedPublicWordbookId(book.id)
    setDailyTargetModalBookId(book.id)
    setContinueAfterTargetUpdateBookId(options?.continueAfterUpdate ? book.id : null)
  }

  function closeDailyTargetModal() {
    if (savingDailyTarget != null) return
    setDailyTargetModalBookId(null)
    setContinueAfterTargetUpdateBookId(null)
  }

  async function updateDailyTarget(target: number) {
    if (!dailyTargetModalBook) return

    const shouldContinue =
      continueAfterTargetUpdateBookId === dailyTargetModalBook.id &&
      target > dailyTargetModalBook.todayCompletedCount

    setSavingDailyTarget(target)
    const updated = await handleUpdatePublicWordbookDailyTarget(dailyTargetModalBook.id, target)
    setSavingDailyTarget(null)

    if (updated) {
      closeDailyTargetModal()
      if (shouldContinue) {
        void handleCreateQuiz('PUBLIC_WORDBOOK', dailyTargetModalBook.id)
      }
    }
  }

  function continuePublicWordbook(book: PublicWordbook) {
    setSelectedPublicWordbookId(book.id)

    if (book.dailyTargetCount <= 0 || isDailyQuotaCompleted(book)) {
      openDailyTargetModal(book, { continueAfterUpdate: true })
      return
    }

    void handleCreateQuiz('PUBLIC_WORDBOOK', book.id)
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
                  {subscribedPublicWordbooks.map((book) => {
                    const progressPercent = getProgressPercent(book)
                    const estimatedDays = getEstimatedDays(book)

                    return (
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
                                onClick={() => continuePublicWordbook(book)}
                                disabled={creatingQuiz}
                              >
                                {creatingQuiz ? '创建中...' : '继续学习'}
                              </button>
                              <button
                                type="button"
                                className="ghost"
                                onClick={() => openDailyTargetModal(book)}
                              >
                                背词数量：{book.dailyTargetCount}个
                              </button>
                              <button
                                type="button"
                                className="ghost"
                                onClick={() => requestReset(book)}
                                disabled={resettingPublicWordbookId === book.id}
                              >
                                {resettingPublicWordbookId === book.id ? '重置中...' : '重置进度'}
                              </button>
                            </div>

                            <div className="subscribed-stat-box">
                              <span>总计：{book.wordCount} 个</span>
                              <span>已背：{book.completedCount} 个</span>
                              <span>错词：{book.wrongCount} 个</span>
                            </div>

                            <div className="subscription-progress-card">
                              <div className="subscription-progress-meta">
                                <span>总进度 {book.completedCount}/{book.wordCount}</span>
                                <span>
                                  {book.dailyTargetCount > 0
                                    ? `完成天数 ${estimatedDays ?? 0} 天`
                                    : '完成天数 未设置'}
                                </span>
                              </div>
                              <div className="subscription-progress-track" aria-hidden="true">
                                <span
                                  className="subscription-progress-fill"
                                  style={{ width: `${progressPercent}%` }}
                                />
                              </div>
                              <div className="subscription-progress-meta subscription-progress-meta--muted">
                                <span>
                                  今日已背 {book.todayCompletedCount}
                                  {book.dailyTargetCount > 0 ? ` / ${book.dailyTargetCount}` : ''}
                                </span>
                                <span>{Math.round(progressPercent)}%</span>
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    )
                  })}
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
                    !selectedPublicWordbookId || !!selectedPublicWordbook?.subscribed || subscribingPublicWordbookId != null
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

      {dailyTargetModalBook && (
        <div className="modal-backdrop daily-target-backdrop" role="presentation" onClick={closeDailyTargetModal}>
          <section
            className="modal-card daily-target-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="daily-target-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="panel-head">
              <div>
                <p className="eyebrow">每日背词配额</p>
                <h3 id="daily-target-title">{dailyTargetModalBook.name}</h3>
              </div>
            </div>

            {dailyTargetModalRequiresUpgrade && (
              <div className="daily-target-modal-hint" role="status" aria-live="polite">
                <strong>今日学习任务已完成。</strong>
                <span>如需继续学习，请把背词数量调整到高于今日已背的数量。</span>
              </div>
            )}

            <div className="daily-target-summary">
              <div className="daily-target-summary-item">
                <span className="meta-label">总计</span>
                <strong>{dailyTargetModalBook.wordCount} 个单词</strong>
              </div>
              <div className="daily-target-summary-item">
                <span className="meta-label">今日已背</span>
                <strong>{dailyTargetModalBook.todayCompletedCount} 个单词</strong>
              </div>
              <div className="daily-target-summary-item">
                <span className="meta-label">完成天数</span>
                <strong>
                  {dailyTargetModalBook.dailyTargetCount > 0
                    ? `${getEstimatedDays(dailyTargetModalBook) ?? 0} 天`
                    : '未设置'}
                </strong>
              </div>
            </div>

            <div className="subscription-progress-card subscription-progress-card--modal">
              <div className="subscription-progress-meta">
                <span>
                  今日进度 {dailyTargetModalBook.todayCompletedCount}
                  {dailyTargetModalBook.dailyTargetCount > 0 ? ` / ${dailyTargetModalBook.dailyTargetCount}` : ' / 未设置'}
                </span>
                <span>总进度 {dailyTargetModalBook.completedCount}/{dailyTargetModalBook.wordCount}</span>
              </div>
              <div className="subscription-progress-track" aria-hidden="true">
                <span
                  className="subscription-progress-fill"
                  style={{ width: `${getProgressPercent(dailyTargetModalBook)}%` }}
                />
              </div>
              <div className="subscription-progress-meta subscription-progress-meta--muted">
                <span>完成比例 {Math.round(getProgressPercent(dailyTargetModalBook))}%</span>
                <span>
                  今日剩余额度{' '}
                  {dailyTargetModalBook.dailyTargetCount > 0
                    ? Math.max(0, dailyTargetModalBook.dailyTargetCount - dailyTargetModalBook.todayCompletedCount)
                    : 0}
                </span>
              </div>
            </div>

            <div className="daily-target-list" role="list">
              {dailyTargetOptions.map((target) => {
                const estimatedDays = getEstimatedDays(dailyTargetModalBook, target) ?? 0
                const isActive = dailyTargetModalBook.dailyTargetCount === target
                const isSaving = savingDailyTarget === target
                const isBlocked = dailyTargetModalBook.todayCompletedCount >= target
                const remainingToday = Math.max(0, target - dailyTargetModalBook.todayCompletedCount)
                const optionClassName = ['daily-target-option', isActive ? 'active' : '', isBlocked ? 'blocked' : '']
                  .filter(Boolean)
                  .join(' ')
                const optionLabel = isBlocked
                  ? '今日不可选'
                  : isSaving
                    ? '保存中...'
                    : isActive
                      ? '当前配额'
                      : '选择'

                return (
                  <button
                    key={target}
                    type="button"
                    role="listitem"
                    className={optionClassName}
                    disabled={savingDailyTarget != null || isBlocked}
                    onClick={() => void updateDailyTarget(target)}
                  >
                    <div>
                      <strong>{target} 个/天</strong>
                      <span>
                        {remainingToday > 0 ? `今日还可再背 ${remainingToday} 个` : '今日额度已用完'}
                      </span>
                      <span>{estimatedDays} 天完成</span>
                    </div>
                    <span className="daily-target-option-mark">{optionLabel}</span>
                  </button>
                )
              })}
            </div>

            <div className="unsubscribe-actions">
              <button type="button" className="ghost" disabled={savingDailyTarget != null} onClick={closeDailyTargetModal}>
                关闭
              </button>
            </div>
          </section>
        </div>
      )}

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

      {resetTarget && (
        <div className="modal-backdrop unsubscribe-backdrop" role="presentation">
          <section className="modal-card unsubscribe-modal" role="dialog" aria-modal="true" aria-labelledby="reset-title">
            <div className="panel-head">
              <div>
                <p className="eyebrow">重置进度</p>
                <h3 id="reset-title">是否确认重置？</h3>
              </div>
            </div>
            <div className="meta">
              <span className="meta-label">词书</span>
              <span className="meta-value">{resetTarget.name}</span>
            </div>
            <div className="meta">
              <span className="meta-label">说明</span>
              <span className="meta-value">重置后将清空这本公共词书的总进度、今日进度和错词统计。</span>
            </div>
            <div className="unsubscribe-actions">
              <button
                type="button"
                className="primary danger"
                disabled={resettingPublicWordbookId === resetTarget.id}
                onClick={() => void confirmReset()}
              >
                {resettingPublicWordbookId === resetTarget.id ? '重置中...' : '确认重置'}
              </button>
              <button
                type="button"
                className="ghost"
                disabled={resettingPublicWordbookId === resetTarget.id}
                onClick={() => setResetTarget(null)}
              >
                取消
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  )
}
