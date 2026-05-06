import { useMemo, useState } from 'react'
import type { PublicWordbook } from '../api/modules/search'
import { useAppStateContext } from '../context/AppStateContext'
import { getWordbookArtwork, type WordbookArtworkKind } from '../utils/wordbookArtwork'

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

function WordbookArtwork({
  name,
  kind,
  className,
}: {
  name: string
  kind: WordbookArtworkKind
  className: string
}) {
  const src = getWordbookArtwork(name, kind)
  const [imageError, setImageError] = useState(false)

  if (!src || imageError) {
    return (
      <div className={`${className} is-fallback`} aria-hidden="true">
        <span className="library-artwork-fallback-mark" />
        <span className="library-artwork-fallback-text">{kind === 'imported' ? 'BOOK' : '词书'}</span>
      </div>
    )
  }

  return (
    <div className={`${className} has-artwork`} aria-hidden="true">
      <img src={src} alt="" loading="lazy" onError={() => setImageError(true)} />
    </div>
  )
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
    quizState,
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
  const selectedImportedWordbook = wordbooks.find((book) => book.id === selectedWordbookId) ?? wordbooks[0] ?? null
  const importedWordTotal = useMemo(() => wordbooks.reduce((total, book) => total + book.wordCount, 0), [wordbooks])
  const importedClearedTotal = useMemo(() => wordbooks.reduce((total, book) => total + book.clearedCount, 0), [wordbooks])
  const subscribedWordCount = useMemo(
    () => subscribedPublicWordbooks.reduce((total, book) => total + book.wordCount, 0),
    [subscribedPublicWordbooks],
  )
  const inProgressCount = useMemo(
    () =>
      subscribedPublicWordbooks.filter(
        (book) => book.completedCount > 0 && book.completedCount < book.wordCount,
      ).length,
    [subscribedPublicWordbooks],
  )
  const masteredCount = useMemo(
    () => subscribedPublicWordbooks.filter((book) => book.wordCount > 0 && book.completedCount >= book.wordCount).length,
    [subscribedPublicWordbooks],
  )
  const featuredSubscription =
    (selectedPublicWordbook?.subscribed ? selectedPublicWordbook : null) ?? subscribedPublicWordbooks[0] ?? null
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
  const importedProgressPercent =
    selectedImportedWordbook && selectedImportedWordbook.wordCount > 0
      ? Math.round((selectedImportedWordbook.clearedCount / selectedImportedWordbook.wordCount) * 100)
      : 0
  const learningImportedWordbookId = quizState?.session.targetType === 'USER_WORDBOOK' ? quizState.session.targetId : null
  const learningPublicWordbookId = quizState?.session.targetType === 'PUBLIC_WORDBOOK' ? quizState.session.targetId : null

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
          <div className="library-hero-copy">
            <p className="eyebrow">词书仓库</p>
            <h2 id="library-title">像素词库</h2>
            <p className="library-hero-summary">
              {featuredSubscription
                ? `当前聚焦《${featuredSubscription.name}》，可以从已保存的进度继续学习。`
                : '把订阅的公共词书收纳进来，随时回到你的学习节奏。'}
            </p>
            <div className="library-hero-highlights" aria-label="词书订阅概览">
              <div className="library-hero-highlight">
                <span>已订阅</span>
                <strong>{subscribedPublicWordbooks.length} 本</strong>
              </div>
              <div className="library-hero-highlight">
                <span>总词量</span>
                <strong>{subscribedWordCount} 词</strong>
              </div>
              <div className="library-hero-highlight">
                <span>进行中</span>
                <strong>{inProgressCount} 本</strong>
              </div>
            </div>
          </div>
          <div className="library-hero-books">
            <div className="library-hero-book-stack">
              <article className="library-hero-book library-hero-book--front">
                <span className="library-hero-book-mark">{featuredSubscription?.name ?? '等待订阅'}</span>
                <strong>已订阅词书</strong>
                <b>
                  {subscribedPublicWordbooks.length}
                  <small> 本</small>
                </b>
                <span className="library-hero-book-note">
                  {featuredSubscription
                    ? `当前进度 ${featuredSubscription.completedCount}/${featuredSubscription.wordCount}`
                    : '订阅后这里会自动更新你的学习数据'}
                </span>
              </article>

              <article className="library-hero-book library-hero-book--spine library-hero-book--learning">
                <span className="library-hero-spine-title">正在学习</span>
                <strong className="library-hero-spine-value">{inProgressCount} 本</strong>
              </article>

              <article className="library-hero-book library-hero-book--spine library-hero-book--mastered">
                <span className="library-hero-spine-title">已掌握</span>
                <strong className="library-hero-spine-value">{masteredCount} 本</strong>
              </article>

              <article className="library-hero-book library-hero-book--spine library-hero-book--words">
                <span className="library-hero-spine-title">总词量</span>
                <strong className="library-hero-spine-value">{subscribedWordCount}</strong>
              </article>
            </div>
          </div>
        </section>

        <div className="pixel-library-grid single">
          <div className="list pixel-panel pixel-panel-menu">
            <section className="library-stage-card library-stage-card--imported">
              <div className="toolbar pixel-toolbar library-stage-head">
                <div>
                  <p className="eyebrow">我的导入词书</p>
                  <h4>共 {wordbooks.length} 本</h4>
                </div>
              </div>

              {selectedImportedWordbook ? (
                <div className="library-dashboard-grid">
                  <article className="library-focus-card imported-focus-card">
                    <div className="library-focus-bookmark" aria-hidden="true" />
                    <WordbookArtwork
                      name={selectedImportedWordbook.name}
                      kind="imported"
                      className="library-focus-book-icon"
                    />
                    <div className="library-focus-copy">
                      <strong>{selectedImportedWordbook.name}</strong>
                      <span className="library-focus-status library-focus-status--dynamic">
                        {learningImportedWordbookId === selectedImportedWordbook.id ? '正在学习' : '当前查看'}
                      </span>
                      <span className="library-focus-status">当前学习中</span>
                      <span>
                        {selectedImportedWordbook.wordCount} 词 / 已学 {selectedImportedWordbook.clearedCount} 词
                      </span>
                    </div>
                    <div className="library-focus-progress">
                      <div className="library-focus-progress-meta">
                        <span>当前进度</span>
                        <strong>{importedProgressPercent}%</strong>
                      </div>
                      <div className="library-focus-progress-track" aria-hidden="true">
                        <span
                          className="library-focus-progress-fill"
                          style={{ width: `${importedProgressPercent}%` }}
                        />
                      </div>
                    </div>
                  </article>

                  <div className="library-kpi-grid">
                    <article className="library-kpi-card">
                      <span>我的词书</span>
                      <strong>{wordbooks.length}</strong>
                      <small>已导入</small>
                    </article>
                    <article className="library-kpi-card">
                      <span>累计单词</span>
                      <strong>{importedWordTotal}</strong>
                      <small>已收纳</small>
                    </article>
                    <article className="library-kpi-card">
                      <span>已学单词</span>
                      <strong>{importedClearedTotal}</strong>
                      <small>持续推进</small>
                    </article>
                  </div>
                </div>
              ) : (
                <div className="meta">
                  <span className="meta-label">导入</span>
                  <span className="meta-value">还没有导入词书。</span>
                </div>
              )}

              {wordbooks.length > 0 ? (
                <div className="library-book-switcher" role="list" aria-label="导入词书列表">
                  {wordbooks.map((book) => (
                    <button
                      key={book.id}
                      type="button"
                      role="listitem"
                      className={book.id === selectedWordbookId ? 'book library-switch-book active' : 'book library-switch-book'}
                      onClick={() => setSelectedWordbookId(book.id)}
                    >
                      <strong>
                        {book.name}
                        {learningImportedWordbookId === book.id ? <em className="word-notebook-status-pill">正在学习</em> : null}
                      </strong>
                      <span>
                        {book.wordCount} 词 / 已学 {book.clearedCount}
                      </span>
                    </button>
                  ))}
                </div>
              ) : null}
            </section>

            <div className="public-wordbooks pixel-section library-stage-card library-stage-card--subscribed">
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

              <div className="library-kpi-grid library-kpi-grid--subscribed">
                <article className="library-kpi-card">
                  <span>全部</span>
                  <strong>{subscribedPublicWordbooks.length}</strong>
                  <small>已订阅</small>
                </article>
                <article className="library-kpi-card">
                  <span>进行中</span>
                  <strong>{inProgressCount}</strong>
                  <small>持续学习</small>
                </article>
                <article className="library-kpi-card">
                  <span>已完成</span>
                  <strong>{masteredCount}</strong>
                  <small>已掌握</small>
                </article>
                <article className="library-kpi-card">
                  <span>收藏词量</span>
                  <strong>{subscribedWordCount}</strong>
                  <small>累计单词</small>
                </article>
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
                            className={book.id === selectedPublicWordbookId ? 'book library-subscription-book active' : 'book library-subscription-book'}
                            onClick={() => selectSubscribedWordbook(book)}
                          >
                            <WordbookArtwork
                              name={book.name}
                              kind="public"
                              className="library-subscription-book-cover"
                            />
                            <span className="library-subscription-book-main">
                              <strong>
                                {book.name}
                                {learningPublicWordbookId === book.id ? <em className="word-notebook-status-pill">正在学习</em> : null}
                              </strong>
                              <span className="library-subscription-book-tags">
                                <small>{book.tag || '公共词书'}</small>
                                <small>{book.licenseName}</small>
                              </span>
                              <span>
                                已学 {book.completedCount} / {book.wordCount} 词
                              </span>
                            </span>
                            <span className="library-subscription-book-side">
                              <small>错词 {book.wrongCount}</small>
                              <small>{Math.round(progressPercent)}%</small>
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
                            <div className="subscribed-action-topline">
                              <div className="subscribed-action-intro">
                                <strong>{book.name}</strong>
                                <span>
                                  今日已学 {book.todayCompletedCount}
                                  {book.dailyTargetCount > 0 ? ` / ${book.dailyTargetCount}` : ' 词'}
                                </span>
                              </div>
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
                                  目标 {book.dailyTargetCount || 0} 词/天
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
                            </div>

                            <div className="subscribed-stat-box">
                              <span>总词量：{book.wordCount}</span>
                              <span>已掌握：{book.completedCount}</span>
                              <span>错词单词：{book.wrongCount}</span>
                              <span>完成预计：{book.dailyTargetCount > 0 ? `${estimatedDays ?? 0} 天` : '未设置'}</span>
                            </div>

                            <div className="subscription-progress-card">
                              <div className="subscription-progress-meta">
                                <span>总进度 {book.completedCount}/{book.wordCount}</span>
                                <span>{Math.round(progressPercent)}%</span>
                              </div>
                              <div className="subscription-progress-track" aria-hidden="true">
                                <span
                                  className="subscription-progress-fill"
                                  style={{ width: `${progressPercent}%` }}
                                />
                              </div>
                              <div className="subscription-progress-meta subscription-progress-meta--muted">
                                <span>
                                  今日进度 {book.todayCompletedCount}
                                  {book.dailyTargetCount > 0 ? ` / ${book.dailyTargetCount}` : ' / 未设置'}
                                </span>
                                <span>正确尝试 {book.todayCorrectAttempts}/{book.todayTotalAttempts}</span>
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
