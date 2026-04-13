import type { QuizMode, QuizSessionState } from '../types'

interface QuizViewProps {
  quizMode: QuizMode
  onQuizModeChange: (mode: QuizMode) => void
  quizState: QuizSessionState | null
  onCreateQuiz: () => void
  onAnswer: (option: string) => void
}

export function QuizView({
  quizMode,
  onQuizModeChange,
  quizState,
  onCreateQuiz,
  onAnswer,
}: QuizViewProps) {
  return (
    <div className="list">
      <div className="toolbar">
        <label>
          <span>题型模式</span>
          <select
            value={quizMode}
            onChange={(e) => onQuizModeChange(e.target.value as QuizMode)}
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
      {quizState?.currentQuestion ? (
        <>
          <div className="card big">
            <strong>{quizState.currentQuestion.promptText}</strong>
            <span>
              {quizState.currentQuestion.promptType === 'CN_TO_EN'
                ? '中文 -> 英文'
                : '英文 -> 中文'}
            </span>
          </div>
          <div className="options">
            {quizState.currentQuestion.options.map((option) => (
              <button
                key={option}
                type="button"
                className="option"
                onClick={() => void onAnswer(option)}
              >
                {option}
              </button>
            ))}
          </div>
        </>
      ) : (
        <div className="card">
          <strong>{quizState ? '本轮词书已经斩完' : '还没有开始斩词'}</strong>
          <span>先从我的词书里选择一本词书。</span>
        </div>
      )}
    </div>
  )
}
