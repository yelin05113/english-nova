import { SearchCard } from './SearchCard'
import type { WordSearchResponse } from '../types'

interface SearchViewProps {
  searchQuery: string
  onSearchQueryChange: (value: string) => void
  searchResult: WordSearchResponse
}

export function SearchView({ searchQuery, onSearchQueryChange, searchResult }: SearchViewProps) {
  return (
    <div className="list">
      <label>
        <span>搜索单词、中文含义或例句</span>
        <input value={searchQuery} onChange={(e) => onSearchQueryChange(e.target.value)} />
      </label>
      <div className="split">
        <div className="list">
          <h4>公共词库</h4>
          {searchResult.publicHits.map((item) => (
            <SearchCard key={`p-${item.entryId}`} item={item} />
          ))}
        </div>
        <div className="list">
          <h4>我的词库</h4>
          {searchResult.myHits.map((item) => (
            <SearchCard key={`m-${item.entryId}`} item={item} />
          ))}
        </div>
      </div>
    </div>
  )
}
