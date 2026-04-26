import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'
import type { PublicWordbookProgressSnapshot, QuizAnswerResult } from '../api/modules/quiz'

interface OptionState {
  option: string
  status: 'correct' | 'wrong' | 'idle'
}

interface Feedback {
  correct: boolean
  correctOption: string
  dailyTargetJustCompleted: boolean
}

interface MeaningLine {
  partOfSpeech: string
  meaning: string
}

const PART_OF_SPEECH_PATTERN = /(^|[\s\n])((?:abbr|adj|adv|aux|conj|int|num|prep|pron|vt|vi|art|pl|n|v|a)\.)\s*/gi

export function QuizView() {
  const {
    agenda,
    progress,
    entries,
    selectedWordbook,
    selectedPublicWordbook,
    quizState,
    creatingQuiz,
    handleCreateQuiz,
    handleResetPublicWordbookProgress,
    handleAnswer,
    advanceQuiz,
  } = useAppStateContext()
  const [optionStates, setOptionStates] = useState<OptionState[]>([])
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [dailyCompletion, setDailyCompletion] = useState<PublicWordbookProgressSnapshot | null>(null)
  const [pendingAdvance, setPendingAdvance] = useState<QuizAnswerResult | null>(null)
  const [latestAnswerResult, setLatestAnswerResult] = useState<QuizAnswerResult | null>(null)
  const [localFirstChoiceStats, setLocalFirstChoiceStats] = useState({ correct: 0, total: 0 })
  const [firstChoiceBaseline, setFirstChoiceBaseline] = useState({ correct: 0, total: 0 })
  const feedbackRef = useRef<HTMLDivElement>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const prevQuestionId = useRef<number | null>(null)

  const question = quizState?.currentQuestion
  const isPublicQuiz = quizState?.session.targetType === 'PUBLIC_WORDBOOK'

  useEffect(() => {
    setFirstChoiceBaseline({
      correct: quizState?.session.todayCorrectAttempts ?? 0,
      total: quizState?.session.todayTotalAttempts ?? 0,
    })
    setLocalFirstChoiceStats({ correct: 0, total: 0 })
  }, [quizState?.session.id])

  useEffect(() => {
    const currentId = question?.attemptId ?? null
    if (currentId !== prevQuestionId.current) {
      prevQuestionId.current = currentId
      // Reset answer UI when the backend advances to a different question.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFeedback(null)
      setPendingAdvance(null)
      setLatestAnswerResult(null)
      setSubmitting(false)
      setOptionStates(
        (question?.options ?? []).map((option) => ({
          option,
          status: 'idle',
        })),
      )

      if (currentId != null) {
        setDailyCompletion(null)
      }
    }
  }, [question?.attemptId, question?.options])

  function onCreateQuiz() {
    return handleCreateQuiz(quizState?.session.targetType ?? 'USER_WORDBOOK', quizState?.session.targetId)
  }

  function triggerDailyCompletion(snapshot: PublicWordbookProgressSnapshot | null) {
    if (!snapshot) {
      return
    }

    setDailyCompletion(snapshot)
  }

  const stopPromptAudio = useCallback(() => {
    audioRef.current?.pause()
    if (audioRef.current) {
      audioRef.current.currentTime = 0
    }
    audioRef.current = null
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
  }, [])

  const speakPrompt = useCallback((word: string) => {
    if (!('speechSynthesis' in window)) {
      return
    }
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(word)
    utterance.lang = 'en-US'
    utterance.volume = 1
    window.speechSynthesis.speak(utterance)
  }, [])

  const playPromptAudio = useCallback(
    (audioUrl: string, word: string) => {
      stopPromptAudio()
      if (audioUrl) {
        const audio = new Audio(audioUrl)
        audio.volume = 1
        audio.preload = 'auto'
        audioRef.current = audio
        void audio.play().catch(() => speakPrompt(word))
        return
      }
      speakPrompt(word)
    },
    [speakPrompt, stopPromptAudio],
  )

  useEffect(() => {
    if (!isPublicQuiz || !question) {
      return
    }

    const timer = window.setTimeout(() => {
      playPromptAudio(question.audioUrl, question.promptText)
    }, 80)

    return () => {
      window.clearTimeout(timer)
      stopPromptAudio()
    }
  }, [isPublicQuiz, playPromptAudio, question?.attemptId, question?.audioUrl, question?.promptText, stopPromptAudio])

  function replayCurrentWord() {
    if (!isPublicQuiz || !question) {
      return
    }
    playPromptAudio(question.audioUrl, question.promptText)
  }

  function splitMeaningItems(value: string) {
    return value
      .replace(/\\n/g, '\n')
      .replace(/\s+/g, ' ')
      .split(/[;；。。，,、/]+/)
      .map((part) => part.trim())
      .filter(Boolean)
  }

  function toPartOfSpeechMeaningLines(value: string): MeaningLine[] {
    const normalized = value.replace(/\\n/g, '\n').trim()
    if (!normalized) return []

    const matches = Array.from(normalized.matchAll(PART_OF_SPEECH_PATTERN))
    if (matches.length === 0) {
      const fallback = splitMeaningItems(normalized).join('、')
      return fallback ? [{ partOfSpeech: '', meaning: fallback }] : []
    }

    const grouped = new Map<string, string[]>()
    matches.forEach((match, index) => {
      const partOfSpeech = match[2].toLowerCase()
      const contentStart = match.index + match[0].length
      const contentEnd = matches[index + 1]?.index ?? normalized.length
      const items = splitMeaningItems(normalized.slice(contentStart, contentEnd))
      if (items.length === 0) {
        return
      }
      grouped.set(partOfSpeech, [...(grouped.get(partOfSpeech) ?? []), ...items])
    })

    return Array.from(grouped, ([partOfSpeech, meanings]) => ({
      partOfSpeech,
      meaning: meanings.join('、'),
    }))
  }

  function toPrimaryMeaning(value: string) {
    const primaryMeanings = toPartOfSpeechMeaningLines(value)
      .flatMap((line) => line.meaning.split('、'))
      .map((part) => part.trim())
      .filter(Boolean)
      .slice(0, 2)

    const summary = primaryMeanings.length > 0 ? primaryMeanings.join('、') : value.replace(/\s+/g, ' ').trim()
    return summary.slice(0, 64)
  }

  function handleNextQuestion() {
    if (!pendingAdvance) {
      return
    }
    advanceQuiz(pendingAdvance)
    setPendingAdvance(null)
  }

  function handleResetProgress() {
    if (!isPublicQuiz || !quizState?.session.targetId) {
      return
    }
    setPendingAdvance(null)
    void handleResetPublicWordbookProgress(quizState.session.targetId)
  }

  async function handleOptionClick(option: string) {
    if (submitting || feedback?.correct) return

    const isFirstChoice = optionStates.every((entry) => entry.status === 'idle')
    setSubmitting(true)
    const result = await handleAnswer(option)
    setSubmitting(false)

    if (!result) return
    setLatestAnswerResult(result)
    if (isFirstChoice) {
      setLocalFirstChoiceStats((current) => ({
        correct: current.correct + (result.correct ? 1 : 0),
        total: current.total + 1,
      }))
    }

    setFeedback({
      correct: result.correct,
      correctOption: result.correctOption,
      dailyTargetJustCompleted: result.dailyTargetJustCompleted,
    })

    if (result.correct) {
      setOptionStates((prev) =>
        prev.map((entry) => (entry.option === option ? { ...entry, status: 'correct' } : entry)),
      )

      if (result.dailyTargetJustCompleted) {
        triggerDailyCompletion(result.publicWordbookProgress)
      }

      window.setTimeout(() => feedbackRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 80)
      if (isPublicQuiz) {
        setPendingAdvance(result)
        return
      }
      window.setTimeout(() => advanceQuiz(result), result.dailyTargetJustCompleted ? 1100 : 500)
      return
    }

    setOptionStates((prev) =>
      prev.map((entry) => (entry.option === option ? { ...entry, status: 'wrong' } : entry)),
    )
    window.setTimeout(() => feedbackRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 80)
  }

  function renderDailyCompletionCard(className = 'quiz-daily-complete') {
    if (!dailyCompletion) {
      return null
    }

    return (
      <div className={`card ${className}`}>
        <div className="daily-complete-fireworks" aria-hidden="true">
          <span className="daily-complete-burst daily-complete-burst-a" />
          <span className="daily-complete-burst daily-complete-burst-b" />
          <span className="daily-complete-burst daily-complete-burst-c" />
          <span className="daily-complete-burst daily-complete-burst-d" />
          <span className="daily-complete-burst daily-complete-burst-e" />
        </div>
        <div className="quiz-daily-complete-copy">
          <strong>今日学习任务已完成</strong>
          <span>
            今日已背 {dailyCompletion.todayCompletedCount}/{dailyCompletion.dailyTargetCount}，总进度{' '}
            {dailyCompletion.completedCount}/{dailyCompletion.wordCount}。
          </span>
        </div>
      </div>
    )
  }

  const showDailyCompleteFeedback = feedback?.correct && feedback.dailyTargetJustCompleted && dailyCompletion
  const effectiveSession = latestAnswerResult?.session ?? quizState?.session
  const answeredCount = quizState?.session.answeredQuestions ?? 0
  const totalQuestions = question?.totalQuestions ?? effectiveSession?.totalQuestions ?? 0
  const displayedAnsweredCount =
    question && feedback?.correct
      ? Math.min(totalQuestions, latestAnswerResult?.session.answeredQuestions ?? answeredCount + 1)
      : answeredCount
  const progressPercent = totalQuestions > 0 ? Math.round((displayedAnsweredCount / totalQuestions) * 100) : 0
  const remainingQuestions = Math.max(0, totalQuestions - displayedAnsweredCount)
  const dailyAgendaTarget = (agenda?.newCards ?? 0) + (agenda?.reviewCards ?? 0)
  const latestPublicWordbookProgress = latestAnswerResult?.publicWordbookProgress
  const dailyTarget =
    latestPublicWordbookProgress?.dailyTargetCount ?? selectedPublicWordbook?.dailyTargetCount ?? (dailyAgendaTarget || totalQuestions)
  const dailyDone = latestPublicWordbookProgress?.todayCompletedCount ?? selectedPublicWordbook?.todayCompletedCount ?? displayedAnsweredCount
  const dailyPercent = dailyTarget > 0 ? Math.min(100, Math.round((dailyDone / dailyTarget) * 100)) : 0
  const currentTargetName =
    quizState?.session.targetType === 'PUBLIC_WORDBOOK'
      ? (selectedPublicWordbook?.name ?? '公共词书')
      : (selectedWordbook?.name ?? '我的词书')
  const currentTargetTotal =
    quizState?.session.targetType === 'PUBLIC_WORDBOOK'
      ? (selectedPublicWordbook?.wordCount ?? 0)
      : (selectedWordbook?.wordCount ?? progress?.totalWords ?? 0)
  const currentTargetCleared =
    quizState?.session.targetType === 'PUBLIC_WORDBOOK'
      ? (selectedPublicWordbook?.completedCount ?? 0)
      : (selectedWordbook?.clearedCount ?? progress?.clearedWords ?? 0)
  const targetPercent = currentTargetTotal > 0 ? Math.round((currentTargetCleared / currentTargetTotal) * 100) : 0
  const currentWord = question?.currentWord || question?.promptText || '-'
  const rawTodayCorrectAttempts = effectiveSession?.todayCorrectAttempts
  const rawTodayTotalAttempts = effectiveSession?.todayTotalAttempts
  const fallbackTodayCorrectAttempts = firstChoiceBaseline.correct + localFirstChoiceStats.correct
  const fallbackTodayTotalAttempts = firstChoiceBaseline.total + localFirstChoiceStats.total
  const shouldUseLocalFirstChoiceStats = localFirstChoiceStats.total > 0
  const todayTotalAttempts =
    shouldUseLocalFirstChoiceStats
      ? fallbackTodayTotalAttempts
      : typeof rawTodayTotalAttempts === 'number' && rawTodayTotalAttempts > 0
        ? rawTodayTotalAttempts
        : fallbackTodayTotalAttempts
  const todayCorrectAttempts =
    shouldUseLocalFirstChoiceStats
      ? fallbackTodayCorrectAttempts
      : typeof rawTodayTotalAttempts === 'number' && rawTodayTotalAttempts > 0
        ? (rawTodayCorrectAttempts ?? 0)
        : fallbackTodayCorrectAttempts
  const todayQuotaAccuracy = todayTotalAttempts > 0 ? Math.round((todayCorrectAttempts / todayTotalAttempts) * 100) : 0
  const focusWords = entries
    .slice()
    .sort((a, b) => b.difficulty - a.difficulty)
    .slice(0, 3)
  const optionLabels = ['A', 'B', 'C', 'D']

  return (
    <div className="quiz-console">
      <aside className="quiz-rail quiz-rail--left" aria-label="学习设置">
        <section className="quiz-panel quiz-goal-panel">
          <div className="quiz-panel-head">
            <h2>今日目标</h2>
            <Link to="/library">编辑目标</Link>
          </div>
          <div className="quiz-goal-body">
            <div className="quiz-goal-ring" style={{ '--goal-rate': `${dailyPercent}` } as CSSProperties}>
              <span>{dailyPercent}%</span>
              <small>已完成</small>
            </div>
            <div className="quiz-goal-metrics">
              <span>学习新词</span>
              <strong>{agenda?.newCards ?? 0}</strong>
              <span>复习单词</span>
              <strong>{agenda?.reviewCards ?? 0}</strong>
              <small>预计还需 {agenda?.estimatedMinutes ?? 0} 分钟完成</small>
            </div>
          </div>
        </section>

        <section className="quiz-panel quiz-mode-panel">
          <div className="quiz-panel-head">
            <h2>答题方向</h2>
          </div>
          <div className="quiz-mode-grid quiz-mode-grid--single">
            <div className="quiz-mode-card active">
              <span>{isPublicQuiz ? '英文选中文' : '当前练习模式'}</span>
              <small>
                {isPublicQuiz
                  ? '公共词书练习固定显示英文，从当前词书随机抽取 4 个中文释义选项'
                  : '私有词书练习暂不调整，继续沿用当前出题方式'}
              </small>
            </div>
          </div>
        </section>

        <section className="quiz-panel quiz-book-panel">
          <div className="quiz-panel-head">
            <h2>词书信息</h2>
            <span className="quiz-status-pill">{quizState ? '正在学习' : '待开始'}</span>
          </div>
          <div className="quiz-book-line">
            <div className="quiz-book-cover">{currentTargetName.charAt(0).toUpperCase()}</div>
            <div>
              <strong>{currentTargetName}</strong>
              <span>
                已学 {currentTargetCleared} / {currentTargetTotal || '-'}
              </span>
            </div>
          </div>
          <div className="quiz-mini-track" aria-label={`词书进度 ${targetPercent}%`}>
            <span style={{ width: `${targetPercent}%` }} />
          </div>
          <small>词书进度 {targetPercent}%</small>
        </section>
      </aside>

      <main className="quiz-main" aria-label="单词练习">
        {showDailyCompleteFeedback ? renderDailyCompletionCard('quiz-daily-complete quiz-daily-complete--inline') : null}

        <section className="quiz-study-card">
          <div className="quiz-study-head">
            <div>
              <span className="quiz-section-mark">单词练习</span>
              <h2>{question ? '选择正确答案' : '还没有开始背词'}</h2>
            </div>
            <div className="quiz-progress-compact">
              <span className="quiz-progress-line">
                <i style={{ width: `${progressPercent}%` }} />
              </span>
              <strong>
                {displayedAnsweredCount} / {totalQuestions || '-'}
              </strong>
            </div>
          </div>

          {question ? (
            <>
              <div className="quiz-word-block">
                {isPublicQuiz ? (
                  <button type="button" className="quiz-word-audio-button" onClick={replayCurrentWord}>
                    <strong>{question.promptText}</strong>
                  </button>
                ) : (
                  <strong>{question.promptText}</strong>
                )}
                {isPublicQuiz ? (
                  <button
                    type="button"
                    className="quiz-word-audio-button quiz-word-phonetic phonetic-text"
                    onClick={replayCurrentWord}
                  >
                    /{question.phonetic || '-'}/
                  </button>
                ) : null}
                <p>根据英文选择最贴近的中文释义</p>
              </div>

              <div className="options quiz-answer-grid">
                {optionStates.map(({ option, status }, index) => (
                  <button
                    key={option}
                    type="button"
                    className={`option option--${status}`}
                    onClick={() => void handleOptionClick(option)}
                    disabled={submitting || status === 'wrong' || feedback?.correct === true}
                    title={isPublicQuiz ? option : undefined}
                  >
                    <span className="option-letter">{optionLabels[index] ?? index + 1}</span>
                    <span className="option-text">{isPublicQuiz ? toPrimaryMeaning(option) : option}</span>
                    <span className="option-icon">{status === 'correct' ? '✓' : status === 'wrong' ? '✕' : ''}</span>
                  </button>
                ))}
              </div>

              {isPublicQuiz && feedback?.correct ? (
                <div className="quiz-full-meaning">
                  <span>完整释义</span>
                  <div className="quiz-full-meaning-lines">
                    {toPartOfSpeechMeaningLines(feedback.correctOption).map((line, index) => (
                      <p key={`${line.partOfSpeech || 'meaning'}-${index}`}>
                        {line.partOfSpeech ? <b>{line.partOfSpeech}</b> : null}
                        <strong>{line.meaning}</strong>
                      </p>
                    ))}
                  </div>
                </div>
              ) : null}

              <div className="quiz-study-actions">
                {isPublicQuiz && pendingAdvance ? (
                  <button type="button" className="primary" onClick={handleNextQuestion}>
                    下一个
                  </button>
                ) : null}
                <span>{remainingQuestions > 0 ? `剩余 ${remainingQuestions} 题` : '本轮已完成'}</span>
              </div>

              <div
                ref={feedbackRef}
                className={`quiz-feedback${
                  feedback
                    ? feedback.correct
                      ? ' quiz-feedback--correct'
                      : ' quiz-feedback--wrong'
                    : ' quiz-feedback--hidden'
                }${showDailyCompleteFeedback ? ' quiz-feedback--celebrate' : ''}`}
              >
                {showDailyCompleteFeedback ? null : feedback ? (
                  feedback.correct ? (
                    <>
                      <span className="quiz-feedback-icon">✓</span>
                      <span>回答正确，正在前往下一题。</span>
                    </>
                  ) : (
                    <>
                      <span className="quiz-feedback-icon">✕</span>
                      <span>回答错误，请继续选择。</span>
                    </>
                  )
                ) : null}
              </div>
            </>
          ) : (
            <div className="quiz-empty-state">
              <strong>{quizState ? '本轮词书已经斩完' : '选择一本词书开始练习'}</strong>
              <span>从词书页选定学习范围，也可以直接用当前词书创建一轮练习。</span>
              <button type="button" className="primary" onClick={() => void onCreateQuiz()} disabled={creatingQuiz}>
                {creatingQuiz ? '创建中...' : '开始背词'}
              </button>
            </div>
          )}
        </section>
      </main>

      <aside className="quiz-rail quiz-rail--right" aria-label="学习概览">
        <section className="quiz-panel quiz-overview-panel">
          <div className="quiz-panel-head">
            <h2>学习概览</h2>
            <Link to="/progress">查看更多</Link>
          </div>
          <div className="quiz-stat-grid">
            <div className="quiz-stat-tile">
              <span>累计学习</span>
              <strong>{progress?.answeredQuestions ?? displayedAnsweredCount}</strong>
            </div>
            <div className="quiz-stat-tile">
              <span>当前单词</span>
              <strong className="quiz-stat-word">{currentWord}</strong>
            </div>
            <div className="quiz-stat-tile">
              <span>今日配额正确率</span>
              <strong>{todayQuotaAccuracy}%</strong>
              <small>
                {todayCorrectAttempts} / {todayTotalAttempts || '-'}
              </small>
            </div>
            <div className="quiz-stat-tile">
              <span>学习词书</span>
              <strong>{progress?.wordbooks ?? 0}</strong>
            </div>
          </div>
        </section>

        <section className="quiz-panel quiz-focus-panel">
          <div className="quiz-panel-head">
            <h2>薄弱词项</h2>
            <Link to="/library">更多词项</Link>
          </div>
          <div className="quiz-focus-list">
            {focusWords.length > 0 ? (
              focusWords.map((entry) => (
                <div key={entry.id} className="quiz-focus-row">
                  <span>{entry.word}</span>
                  <i aria-label={`难度 ${entry.difficulty}`} style={{ width: `${Math.min(100, entry.difficulty * 20)}%` }} />
                </div>
              ))
            ) : (
              <span className="quiz-muted">完成一轮练习后会沉淀重点词项。</span>
            )}
          </div>
        </section>

        <section className="quiz-panel quiz-actions-panel">
          <div className="quiz-panel-head">
            <h2>快速操作</h2>
          </div>
          <div className="quiz-action-grid">
            <Link to="/search">查单词</Link>
            <Link to="/library">生词本</Link>
            <Link to="/progress">学习记录</Link>
            <button type="button" onClick={handleResetProgress} disabled={!isPublicQuiz || creatingQuiz}>
              重置进度
            </button>
          </div>
        </section>
      </aside>
    </div>
  )
}
