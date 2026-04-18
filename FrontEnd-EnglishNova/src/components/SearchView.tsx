import { useEffect, useRef, useState } from 'react'
import { SearchCard } from './SearchCard'
import { WordDetailModal } from './WordDetailModal'
import { useAppStateContext } from '../context/AppStateContext'
import type { SearchHit, WordDetail } from '../api/modules/search'

export function SearchView() {
  const {
    searchQuery,
    setSearchQuery,
    searchResult,
    searchSuggestions,
    pickSearchSuggestion,
    getWordDetail,
  } = useAppStateContext()

  const onSearchQueryChange = setSearchQuery
  const suggestions = searchSuggestions
  const onPickSuggestion = pickSearchSuggestion
  const onFetchWordDetail = getWordDetail
  const [selectedDetail, setSelectedDetail] = useState<WordDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [showSuggestions, setShowSuggestions] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const searchBoxRef = useRef<HTMLLabelElement | null>(null)

  useEffect(() => {
    return () => {
      audioRef.current?.pause()
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel()
      }
    }
  }, [])

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      if (!searchBoxRef.current?.contains(event.target as Node)) {
        setShowSuggestions(false)
      }
    }

    document.addEventListener('mousedown', handlePointerDown)
    return () => document.removeEventListener('mousedown', handlePointerDown)
  }, [])

  async function openDetail(item: SearchHit) {
    setShowSuggestions(false)
    setDetailLoading(true)
    try {
      const detail = await onFetchWordDetail(item.entryId)
      setSelectedDetail(detail)
      playAudio(detail)
    } finally {
      setDetailLoading(false)
    }
  }

  function closeDetail() {
    audioRef.current?.pause()
    audioRef.current = null
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
    setSelectedDetail(null)
  }

  function playAudio(detail: WordDetail) {
    audioRef.current?.pause()
    audioRef.current = null

    if (detail.audioUrl) {
      const audio = new Audio(detail.audioUrl)
      audioRef.current = audio
      void audio.play().catch(() => speak(detail.word))
      return
    }

    speak(detail.word)
  }

  function speak(word: string) {
    if (!('speechSynthesis' in window)) {
      return
    }
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(word)
    utterance.lang = 'en-US'
    window.speechSynthesis.speak(utterance)
  }

  return (
    <>
      <div className="list">
        <label className="search-box" ref={searchBoxRef}>
          <span>搜索英文单词、中文释义或例句</span>
          <input
            value={searchQuery}
            onChange={(event) => {
              onSearchQueryChange(event.target.value)
              setShowSuggestions(true)
            }}
            onFocus={() => {
              if (searchQuery.trim()) {
                setShowSuggestions(true)
              }
            }}
            onKeyDown={(event) => {
              if (event.key === 'Escape') {
                setShowSuggestions(false)
              }
            }}
          />
          {showSuggestions && searchQuery.trim() && suggestions.length > 0 && (
            <div className="suggestion-list">
              {suggestions.map((item) => (
                <button
                  key={`${item.visibility}-${item.entryId}`}
                  type="button"
                  className="suggestion-item"
                  onClick={() => {
                    setShowSuggestions(false)
                    onPickSuggestion(item.word)
                  }}
                >
                  <strong>{item.word}</strong>
                  <span>{item.visibility === 'PRIVATE' ? '我的词书' : '公共词库'}</span>
                  <small>匹配 {item.matchPercent}%</small>
                </button>
              ))}
            </div>
          )}
        </label>
        <div className="split">
          <div className="list">
            <h4>公共词库</h4>
            {searchResult.publicHits.map((item) => (
              <SearchCard key={`p-${item.entryId}`} item={item} onOpen={openDetail} />
            ))}
          </div>
          <div className="list">
            <h4>我的词库</h4>
            {searchResult.myHits.map((item) => (
              <SearchCard key={`m-${item.entryId}`} item={item} onOpen={openDetail} />
            ))}
          </div>
        </div>
      </div>

      {selectedDetail && (
        <WordDetailModal
          detail={selectedDetail}
          loading={detailLoading}
          onClose={closeDetail}
          onReplayAudio={() => playAudio(selectedDetail)}
        />
      )}
    </>
  )
}
