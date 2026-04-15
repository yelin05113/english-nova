import { useEffect, useRef, useState } from 'react'
import { SearchCard } from './SearchCard'
import { WordDetailModal } from './WordDetailModal'
import { useAppStateContext } from '../context/AppStateContext'
import type { SearchHit, WordDetail } from '../types/types'

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
      <div className="grid gap-[14px]">
        <label className="relative grid gap-2" ref={searchBoxRef}>
          <span className="text-forest-deep">搜索英文单词、中文释义或例句</span>
          <input
            className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
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
            <div className="relative mt-2.5 grid gap-2 p-2.5 max-h-[280px] overflow-auto rounded-[18px] border border-[rgba(82,107,84,0.14)] bg-[rgba(255,252,247,0.98)] shadow-[inset_0_1px_0_rgba(255,255,255,0.4)]">
              {suggestions.map((item) => (
                <button
                  key={`${item.visibility}-${item.entryId}`}
                  type="button"
                  className="grid gap-1 w-full border border-[rgba(88,112,90,0.12)] rounded-[14px] px-[14px] py-3 bg-[rgba(247,244,236,0.92)] text-left text-forest-deep cursor-pointer hover:-translate-y-0.5 transition-transform"
                  onClick={() => {
                    setShowSuggestions(false)
                    onPickSuggestion(item.word)
                  }}
                >
                  <strong>{item.word}</strong>
                  <span className="text-muted">{item.visibility === 'PRIVATE' ? '我的词书' : '公共词库'}</span>
                  <small className="text-muted">匹配 {item.matchPercent}%</small>
                </button>
              ))}
            </div>
          )}
        </label>
        <div className="flex gap-4 items-start max-[1120px]:grid">
          <div className="flex-1 grid gap-[14px]">
            <h4>公共词库</h4>
            {searchResult.publicHits.map((item) => (
              <SearchCard key={`p-${item.entryId}`} item={item} onOpen={openDetail} />
            ))}
          </div>
          <div className="flex-1 grid gap-[14px]">
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
