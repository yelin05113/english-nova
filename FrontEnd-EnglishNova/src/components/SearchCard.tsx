import type { SearchHit } from '../api/modules/search'
import { formatMultilineText } from '../utils/text'

interface SearchCardProps {
  item: SearchHit
  onOpen: (item: SearchHit) => void
}

export function SearchCard({ item, onOpen }: SearchCardProps) {
  const meaningText = formatMultilineText(item.meaningCn)
  const metaText = [item.importSource, formatMultilineText(item.exampleSentence)].filter(Boolean).join('\n')

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
      <small className="search-meta multiline-text">{metaText}</small>
    </button>
  )
}
