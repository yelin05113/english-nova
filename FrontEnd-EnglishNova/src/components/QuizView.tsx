import { useEffect, useRef, useState } from 'react'
import { useAppStateContext } from '../context/AppStateContext'
import type { PublicWordbookProgressSnapshot, QuizMode } from '../api/modules/quiz'

interface OptionState {
  option: string
  status: 'correct' | 'wrong' | 'idle'
}

interface Feedback {
  correct: boolean
  correctOption: string
  dailyTargetJustCompleted: boolean
}

export function QuizView() {
  const { quizMode, setQuizMode, quizState, creatingQuiz, handleCreateQuiz, handleAnswer, advanceQuiz } =
    useAppStateContext()
  const [optionStates, setOptionStates] = useState<OptionState[]>([])
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [dailyCompletion, setDailyCompletion] = useState<PublicWordbookProgressSnapshot | null>(null)
  const feedbackRef = useRef<HTMLDivElement>(null)
  const prevQuestionId = useRef<number | null>(null)

  const question = quizState?.currentQuestion

  useEffect(() => {
    const currentId = question?.attemptId ?? null
    if (currentId !== prevQuestionId.current) {
      prevQuestionId.current = currentId
      setFeedback(null)
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

  function onQuizModeChange(mode: QuizMode) {
    setQuizMode(mode)
  }

  function onCreateQuiz() {
    return handleCreateQuiz(quizState?.session.targetType ?? 'USER_WORDBOOK', quizState?.session.targetId)
  }

  function triggerDailyCompletion(snapshot: PublicWordbookProgressSnapshot | null) {
    if (!snapshot) {
      return
    }

    setDailyCompletion(snapshot)
  }

  async function handleOptionClick(option: string) {
    if (submitting || feedback?.correct) return

    setSubmitting(true)
    const result = await handleAnswer(option)
    setSubmitting(false)

    if (!result) return

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
  const answeredCount = quizState?.session.answeredQuestions ?? 0
  const totalQuestions = question?.totalQuestions ?? quizState?.session.totalQuestions ?? 0
  const displayedAnsweredCount =
    question && feedback?.correct ? Math.min(totalQuestions, answeredCount + 1) : answeredCount
  const progressPercent = totalQuestions > 0 ? Math.round((displayedAnsweredCount / totalQuestions) * 100) : 0

  return (
    <div className="list">
      <div className="toolbar">
        <label>
          <span>题型模式</span>
          <select
            id="quiz-mode"
            name="quizMode"
            value={quizMode}
            onChange={(event) => onQuizModeChange(event.target.value as QuizMode)}
            disabled={!!question}
          >
            <option value="MIXED">混合模式</option>
            <option value="CN_TO_EN">中文选英文</option>
            <option value="EN_TO_CN">英文选中文</option>
          </select>
        </label>
        <button type="button" className="ghost" onClick={() => void onCreateQuiz()} disabled={creatingQuiz}>
          {creatingQuiz ? '创建中...' : '重新开始'}
        </button>
      </div>

      {question ? (
        <>
          {showDailyCompleteFeedback ? renderDailyCompletionCard('quiz-daily-complete quiz-daily-complete--inline') : null}

          <div className="quiz-progress-wrap">
            <div className="quiz-progress-bar" style={{ width: `${progressPercent}%` }} />
            <span className="quiz-progress-label">
              {displayedAnsweredCount} / {totalQuestions}
            </span>
          </div>

          <div className="card big quiz-prompt-card">
            <span className="quiz-type-badge">
              {question.promptType === 'CN_TO_EN' ? '中文 -> 英文' : '英文 -> 中文'}
            </span>
            <strong>{question.promptText}</strong>
          </div>

          <div className="options">
            {optionStates.map(({ option, status }) => (
              <button
                key={option}
                type="button"
                className={`option option--${status}`}
                onClick={() => void handleOptionClick(option)}
                disabled={submitting || status === 'wrong' || feedback?.correct === true}
              >
                <span className="option-icon">{status === 'correct' ? '✓' : status === 'wrong' ? '✕' : ''}</span>
                {option}
              </button>
            ))}
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
        <div className="card">
          <strong>{quizState ? '本轮词书已经斩完' : '还没有开始背词'}</strong>
          <span>先从我的词书里选择一本词书。</span>
        </div>
      )}
    </div>
  )
}
