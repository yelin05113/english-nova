import { useEffect, useRef, useState } from 'react'
import { useAppStateContext } from '../context/AppStateContext'
import type { QuizMode } from '../types/types'

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
    <div className="grid gap-[14px]">
      {/* Toolbar */}
      <div className="flex items-center justify-between gap-4 mb-4 max-[1120px]:grid">
        <label className="grid gap-2">
          <span className="text-forest-deep">题型模式</span>
          <select
            className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
            value={quizMode}
            onChange={(e) => onQuizModeChange(e.target.value as QuizMode)}
            disabled={!!question}
          >
            <option value="MIXED">混合模式</option>
            <option value="CN_TO_EN">中文选四个英文</option>
            <option value="EN_TO_CN">英文选四个中文</option>
          </select>
        </label>
        <button
          type="button"
          className="border border-[rgba(88,112,90,0.16)] bg-[rgba(255,252,247,0.88)] text-forest-deep rounded-2xl px-4 py-[13px] cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5"
          onClick={() => void onCreateQuiz()}
        >
          重新开始
        </button>
      </div>

      {question ? (
        <>
          {/* Progress bar */}
          <div className="relative h-2 rounded-full bg-[rgba(226,234,224,0.78)] overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-[#385d4b] to-[#7aaf6e] transition-[width] duration-[400ms]"
              style={{
                width: `${Math.round((question.progress / question.totalQuestions) * 100)}%`,
              }}
            />
            <span className="absolute right-0 -top-[22px] text-[0.78rem] text-muted tracking-[0.04em]">
              {question.progress} / {question.totalQuestions}
            </span>
          </div>

          {/* Prompt card */}
          <div className="relative border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[22px] p-[18px] pt-[22px] grid gap-2">
            <span className="absolute top-[14px] right-4 text-[0.74rem] px-2.5 py-1 rounded-full bg-[rgba(226,234,224,0.88)] text-muted tracking-[0.04em]">
              {question.promptType === 'CN_TO_EN' ? '中文 → 英文' : '英文 → 中文'}
            </span>
            <strong className="text-[2.2rem] leading-[1.02] text-forest-deep">{question.promptText}</strong>
          </div>

          {/* Options */}
          <div className="grid grid-cols-2 gap-[14px] max-[720px]:grid-cols-1">
            {optionStates.map(({ option, status }) => (
              <button
                key={option}
                type="button"
                className={`border rounded-[20px] p-[18px] flex items-center gap-2.5 text-left cursor-pointer transition-[transform,border-color,background] duration-[160ms] hover:-translate-y-0.5 disabled:cursor-default disabled:translate-y-0 ${
                  status === 'correct'
                    ? 'bg-[rgba(104,179,104,0.18)] border-[rgba(62,140,74,0.45)] text-[#2a6b38] shadow-[0_0_0_2px_rgba(62,140,74,0.22)]'
                    : status === 'wrong'
                      ? 'bg-[rgba(207,90,75,0.14)] border-[rgba(185,60,50,0.40)] text-[#8b3228] shadow-[0_0_0_2px_rgba(185,60,50,0.18)]'
                      : 'bg-[rgba(255,252,247,0.92)] border-[rgba(82,107,84,0.14)] text-forest-deep'
                }`}
                onClick={() => void handleOptionClick(option)}
                disabled={submitting || status === 'wrong' || feedback?.correct === true}
              >
                <span className="text-[1rem] w-[1.1em] shrink-0 font-bold">
                  {status === 'correct' ? '✓' : status === 'wrong' ? '✗' : ''}
                </span>
                {option}
              </button>
            ))}
          </div>

          {/* Feedback banner */}
          <div
            ref={feedbackRef}
            className={`flex items-center gap-2.5 px-[18px] py-[14px] rounded-[18px] text-[0.95rem] min-h-[52px] transition-[opacity,transform] duration-[220ms] ${
              !feedback
                ? 'opacity-0 pointer-events-none translate-y-1'
                : feedback.correct
                  ? 'bg-[rgba(104,179,104,0.16)] border border-[rgba(62,140,74,0.3)] text-[#2a6b38] opacity-100 translate-y-0'
                  : 'bg-[rgba(207,90,75,0.12)] border border-[rgba(185,60,50,0.3)] text-[#8b3228] opacity-100 translate-y-0'
            }`}
          >
            {feedback ? (
              feedback.correct ? (
                <>
                  <span className="text-[1.2rem] shrink-0">🎉</span>
                  <span>回答正确，正在前往下一题…</span>
                </>
              ) : (
                <>
                  <span className="text-[1.2rem] shrink-0">💡</span>
                  <span>回答错误，请继续选择</span>
                </>
              )
            ) : null}
          </div>
        </>
      ) : (
        <div className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[22px] p-[18px] grid gap-2">
          <strong className="text-forest-deep">{quizState ? '本轮词书已经斩完 🎊' : '还没有开始斩词'}</strong>
          <span>先从我的词书里选择一本词书。</span>
        </div>
      )}
    </div>
  )
}
