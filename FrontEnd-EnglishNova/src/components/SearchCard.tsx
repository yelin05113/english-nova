import type { SearchHit } from '../api/modules/search'
import { formatMultilineText } from '../utils/text'

interface SearchCardProps {
  item: SearchHit
  onOpen: (item: SearchHit) => void
}

export function SearchCard({ item, onOpen }: SearchCardProps) {
  const meaningText = formatMultilineText(item.meaningCn)
  const chineseSentenceText = formatMultilineText(item.chineseSentence)
  const englishExampleText = formatMultilineText(item.correctedExampleSentence || item.exampleSentence)
  const metaText = [item.importSource, englishExampleText, chineseSentenceText]
    .filter(Boolean)
    .join('\n')

  return (
    <button type="button" className="card search-card" onClick={() => onOpen(item)}>
      <div className="search-card-head">
        <strong>{item.word}</strong>
        <div className="search-card-side">
          <span className="phonetic-text search-phonetic">{item.phonetic || '-'}</span>
          <span className="search-score">匹配 {item.matchPercent}%</span>
        </div>
      </div>
      <span className="search-meaning multiline-text">{meaningText}</span>
      {item.chineseSentence ? <span className="search-meaning multiline-text">{chineseSentenceText}</span> : null}
      <small className="search-meta multiline-text">{metaText}</small>
    </button>
  )
}
