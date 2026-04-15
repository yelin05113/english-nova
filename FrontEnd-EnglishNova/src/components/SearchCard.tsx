import type { SearchHit } from '../types/types'

interface SearchCardProps {
  item: SearchHit
  onOpen: (item: SearchHit) => void
}

export function SearchCard({ item, onOpen }: SearchCardProps) {
  return (
    <button type="button" className="card search-card" onClick={() => onOpen(item)}>
      <div className="search-card-head">
        <strong>{item.word}</strong>
        <div className="search-card-side">
          <span className="phonetic-text search-phonetic">{item.phonetic || '-'}</span>
          <span className="search-score">匹配 {item.matchPercent}%</span>
        </div>
      </div>
      <span className="search-meaning">{item.meaningCn}</span>
      <small className="search-meta">
        {item.source} | {item.importSource} | {item.exampleSentence}
      </small>
    </button>
  )
}
