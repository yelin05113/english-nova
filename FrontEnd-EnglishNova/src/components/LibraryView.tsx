import type { VocabularyEntry, WordbookProgress, WordbookSummary } from '../types'

interface LibraryViewProps {
  wordbooks: WordbookSummary[]
  selectedWordbookId: number | null
  onSelectWordbook: (id: number) => void
  selectedWordbook: WordbookSummary | null
  wordbookProgress: WordbookProgress | null
  entries: VocabularyEntry[]
  onStartQuiz: () => void
}

export function LibraryView({
  wordbooks,
  selectedWordbookId,
  onSelectWordbook,
  selectedWordbook,
  wordbookProgress,
  entries,
  onStartQuiz,
}: LibraryViewProps) {
  return (
    <div className="split">
      <div className="list">
        {wordbooks.map((book) => (
          <button
            key={book.id}
            type="button"
            className={book.id === selectedWordbookId ? 'book active' : 'book'}
            onClick={() => onSelectWordbook(book.id)}
          >
            <strong>{book.name}</strong>
            <span>
              {book.wordCount} 个单词，已斩 {book.clearedCount} 个
            </span>
          </button>
        ))}
      </div>
      <div className="list">
        <div className="toolbar">
          <div>
            <p className="eyebrow">当前词书</p>
            <h4>{selectedWordbook?.name ?? '未选择'}</h4>
          </div>
          <button type="button" className="primary" onClick={onStartQuiz}>
            开始斩词
          </button>
        </div>
        {wordbookProgress && (
          <div className="meta">
            总词数 {wordbookProgress.wordCount} / 已斩 {wordbookProgress.clearedCount} / 答错中{' '}
            {wordbookProgress.inProgressCount}
          </div>
        )}
        {entries.slice(0, 12).map((entry) => (
          <div key={entry.id} className="card">
            <strong>{entry.word}</strong>
            <span>{entry.meaningCn}</span>
            <small>{entry.exampleSentence}</small>
          </div>
        ))}
      </div>
    </div>
  )
}
