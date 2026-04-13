import type { SearchHit } from '../types'

interface SearchCardProps {
  item: SearchHit
}

export function SearchCard({ item }: SearchCardProps) {
  return (
    <div className="card">
      <strong>{item.word}</strong>
      <span>{item.meaningCn}</span>
      <small>
        {item.source} · {item.exampleSentence}
      </small>
    </div>
  )
}
