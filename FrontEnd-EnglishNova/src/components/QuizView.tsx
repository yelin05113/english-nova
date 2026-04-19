import { useEffect, useRef, useState } from 'react'
import { useAppStateContext } from '../context/AppStateContext'
import type { QuizMode } from '../api/modules/quiz'

interface OptionState {
  option: string
  status: 'correct' | 'wrong' | 'idle'
}

interface Feedback {
  correct: boolean
  correctOption: string
}

export function QuizView() {
  const { quizMode, setQuizMode, quizState, handleCreateQuiz, handleAnswer, advanceQuiz } = useAppStateContext()
  const onQuizModeChange = (mode: QuizMode) => setQuizMode(mode)
  const onCreateQuiz = () => void handleCreateQuiz()
  const onAnswer = handleAnswer
  const onAdvance = advanceQuiz
  const [optionStates, setOptionStates] = useState<OptionState[]>([])
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const feedbackRef = useRef<HTMLDivElement>(null)
  const prevQuestionId = useRef<number | null>(null)

  // Reset local state whenever the question changes
  useEffect(() => {
    const currentId = quizState?.currentQuestion?.attemptId ?? null
    if (currentId !== prevQuestionId.current) {
      prevQuestionId.current = currentId
      setFeedback(null)
      setSubmitting(false)
      setOptionStates(
        (quizState?.currentQuestion?.options ?? []).map((opt) => ({
          option: opt,
          status: 'idle',
        })),
      )
    }
  }, [quizState?.currentQuestion?.attemptId])

  async function handleOptionClick(option: string) {
    if (submitting) return
    if (feedback?.correct) return

    setSubmitting(true)
    const result = await onAnswer(option)
    setSubmitting(false)

    if (!result) return

    setFeedback({ correct: result.correct, correctOption: result.correctOption })

    if (result.correct) {
      // Mark this option green
      setOptionStates((prev) =>
        prev.map((o) => (o.option === option ? { ...o, status: 'correct' } : o)),
      )
      // Scroll feedback into view
      setTimeout(() => feedbackRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 80)
      // Auto-advance shortly after the user sees the correct answer.
      setTimeout(() => onAdvance(result), 500)
    } else {
      // Keep the wrong option marked and allow the user to continue trying.
      setOptionStates((prev) =>
        prev.map((o) => (o.option === option ? { ...o, status: 'wrong' } : o)),
      )
      setTimeout(() => feedbackRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 80)
    }
  }

  const question = quizState?.currentQuestion

  return (
    <div className="list">
      <div className="toolbar">
        <label>
          <span>题型模式</span>
          <select
            value={quizMode}
            onChange={(e) => onQuizModeChange(e.target.value as QuizMode)}
            disabled={!!question}
          >
            <option value="MIXED">混合模式</option>
            <option value="CN_TO_EN">中文选四个英文</option>
            <option value="EN_TO_CN">英文选四个中文</option>
          </select>
        </label>
        <button type="button" className="ghost" onClick={() => void onCreateQuiz()}>
          重新开始
        </button>
      </div>

      {question ? (
        <>
          {/* Progress bar */}
          <div className="quiz-progress-wrap">
            <div
              className="quiz-progress-bar"
              style={{
                width: `${Math.round((question.progress / question.totalQuestions) * 100)}%`,
              }}
            />
            <span className="quiz-progress-label">
              {question.progress} / {question.totalQuestions}
            </span>
          </div>

          {/* Prompt card */}
          <div className="card big quiz-prompt-card">
            <span className="quiz-type-badge">
              {question.promptType === 'CN_TO_EN' ? '中文 → 英文' : '英文 → 中文'}
            </span>
            <strong>{question.promptText}</strong>
          </div>

          {/* Options */}
          <div className="options">
            {optionStates.map(({ option, status }) => (
              <button
                key={option}
                type="button"
                className={`option option--${status}`}
                onClick={() => void handleOptionClick(option)}
                disabled={submitting || status === 'wrong' || feedback?.correct === true}
              >
                <span className="option-icon">
                  {status === 'correct' ? '✓' : status === 'wrong' ? '✗' : ''}
                </span>
                {option}
              </button>
            ))}
          </div>

          {/* Feedback banner */}
          <div ref={feedbackRef} className={`quiz-feedback${feedback ? (feedback.correct ? ' quiz-feedback--correct' : ' quiz-feedback--wrong') : ' quiz-feedback--hidden'}`}>
            {feedback ? (
              feedback.correct ? (
                <>
                  <span className="quiz-feedback-icon">🎉</span>
                  <span>回答正确，正在前往下一题…</span>
                </>
              ) : (
                <>
                  <span className="quiz-feedback-icon">💡</span>
                  <span>回答错误，请继续选择</span>
                </>
              )
            ) : null}
          </div>
        </>
      ) : (
        <div className="card">
          <strong>{quizState ? '本轮词书已经斩完 🎊' : '还没有开始斩词'}</strong>
          <span>先从我的词书里选择一本词书。</span>
        </div>
      )}
    </div>
  )
}
