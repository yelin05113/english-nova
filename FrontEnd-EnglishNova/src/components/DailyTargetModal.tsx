import type { PublicWordbook } from '../api/modules/search'

export function buildDailyTargetOptions(wordCount: number) {
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

export function getProgressPercent(book: PublicWordbook) {
  if (book.wordCount <= 0) {
    return 0
  }
  return Math.min(100, Math.max(0, (book.completedCount / book.wordCount) * 100))
}

export function getEstimatedDays(book: PublicWordbook, targetCount = book.dailyTargetCount) {
  if (targetCount <= 0) {
    return null
  }
  const remainingWords = Math.max(0, book.wordCount - book.completedCount)
  if (remainingWords <= 0) {
    return 0
  }
  return Math.ceil(remainingWords / targetCount)
}

export function isDailyQuotaCompleted(book: PublicWordbook) {
  return book.dailyTargetCount > 0 && book.todayCompletedCount >= book.dailyTargetCount
}

interface DailyTargetModalProps {
  book: PublicWordbook
  savingDailyTarget: number | null
  continueAfterTargetUpdate?: boolean
  onClose: () => void
  onSelectTarget: (target: number) => void
}

export function DailyTargetModal({
  book,
  savingDailyTarget,
  continueAfterTargetUpdate = false,
  onClose,
  onSelectTarget,
}: DailyTargetModalProps) {
  const dailyTargetOptions = buildDailyTargetOptions(book.wordCount)
  const dailyTargetModalRequiresUpgrade = continueAfterTargetUpdate && isDailyQuotaCompleted(book)

  return (
    <div className="modal-backdrop daily-target-backdrop" role="presentation" onClick={onClose}>
      <section
        className="modal-card daily-target-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="daily-target-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="panel-head">
          <div>
            <p className="eyebrow">{'\u6bcf\u65e5\u80cc\u8bcd\u914d\u989d'}</p>
            <h3 id="daily-target-title">{book.name}</h3>
          </div>
        </div>

        {dailyTargetModalRequiresUpgrade && (
          <div className="daily-target-modal-hint" role="status" aria-live="polite">
            <strong>{'\u4eca\u65e5\u5b66\u4e60\u4efb\u52a1\u5df2\u5b8c\u6210\u3002'}</strong>
            <span>{'\u5982\u9700\u7ee7\u7eed\u5b66\u4e60\uff0c\u8bf7\u628a\u80cc\u8bcd\u6570\u91cf\u8c03\u6574\u5230\u9ad8\u4e8e\u4eca\u65e5\u5df2\u80cc\u7684\u6570\u91cf\u3002'}</span>
          </div>
        )}

        <div className="daily-target-summary">
          <div className="daily-target-summary-item">
            <span className="meta-label">{'\u603b\u8ba1'}</span>
            <strong>{`${book.wordCount} \u4e2a\u5355\u8bcd`}</strong>
          </div>
          <div className="daily-target-summary-item">
            <span className="meta-label">{'\u4eca\u65e5\u5df2\u80cc'}</span>
            <strong>{`${book.todayCompletedCount} \u4e2a\u5355\u8bcd`}</strong>
          </div>
          <div className="daily-target-summary-item">
            <span className="meta-label">{'\u5b8c\u6210\u5929\u6570'}</span>
            <strong>
              {book.dailyTargetCount > 0
                ? `${getEstimatedDays(book) ?? 0} \u5929`
                : '\u672a\u8bbe\u7f6e'}
            </strong>
          </div>
        </div>

        <div className="subscription-progress-card subscription-progress-card--modal">
          <div className="subscription-progress-meta">
            <span>
              {'\u4eca\u65e5\u8fdb\u5ea6'} {book.todayCompletedCount}
              {book.dailyTargetCount > 0 ? ` / ${book.dailyTargetCount}` : ' / \u672a\u8bbe\u7f6e'}
            </span>
            <span>{`\u603b\u8fdb\u5ea6 ${book.completedCount}/${book.wordCount}`}</span>
          </div>
          <div className="subscription-progress-track" aria-hidden="true">
            <span className="subscription-progress-fill" style={{ width: `${getProgressPercent(book)}%` }} />
          </div>
          <div className="subscription-progress-meta subscription-progress-meta--muted">
            <span>{`\u5b8c\u6210\u6bd4\u4f8b ${Math.round(getProgressPercent(book))}%`}</span>
            <span>
              {'\u4eca\u65e5\u5269\u4f59\u989d\u5ea6 '}
              {book.dailyTargetCount > 0 ? Math.max(0, book.dailyTargetCount - book.todayCompletedCount) : 0}
            </span>
          </div>
        </div>

        <div className="daily-target-list" role="list">
          {dailyTargetOptions.map((target) => {
            const estimatedDays = getEstimatedDays(book, target) ?? 0
            const isActive = book.dailyTargetCount === target
            const isSaving = savingDailyTarget === target
            const isBlocked = book.todayCompletedCount >= target
            const remainingToday = Math.max(0, target - book.todayCompletedCount)
            const optionClassName = ['daily-target-option', isActive ? 'active' : '', isBlocked ? 'blocked' : '']
              .filter(Boolean)
              .join(' ')
            const optionLabel = isBlocked
              ? '\u4eca\u65e5\u4e0d\u53ef\u9009'
              : isSaving
                ? '\u4fdd\u5b58\u4e2d...'
                : isActive
                  ? '\u5f53\u524d\u914d\u989d'
                  : '\u9009\u62e9'

            return (
              <button
                key={target}
                type="button"
                role="listitem"
                className={optionClassName}
                disabled={savingDailyTarget != null || isBlocked}
                onClick={() => onSelectTarget(target)}
              >
                <div>
                  <strong>{`${target} \u4e2a / \u5929`}</strong>
                  <span>
                    {remainingToday > 0
                      ? `\u4eca\u65e5\u8fd8\u53ef\u518d\u80cc ${remainingToday} \u4e2a`
                      : '\u4eca\u65e5\u989d\u5ea6\u5df2\u7528\u5b8c'}
                  </span>
                  <span>{`${estimatedDays} \u5929\u5b8c\u6210`}</span>
                </div>
                <span className="daily-target-option-mark">{optionLabel}</span>
              </button>
            )
          })}
        </div>

        <div className="unsubscribe-actions">
          <button type="button" className="ghost" disabled={savingDailyTarget != null} onClick={onClose}>
            {'\u5173\u95ed'}
          </button>
        </div>
      </section>
    </div>
  )
}
