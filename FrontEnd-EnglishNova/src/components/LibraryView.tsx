import { useEffect, useRef, useState } from 'react'
import type { SearchHit, WordDetail } from '../api/modules/search'
import { useAppStateContext } from '../context/AppStateContext'
import { formatMultilineText } from '../utils/text'
import { SearchCard } from './SearchCard'
import { WordDetailModal } from './WordDetailModal'

export function LibraryView() {
  const {
    wordbooks,
    selectedWordbookId,
    setSelectedWordbookId,
    selectedWordbook,
    wordbookProgress,
    entries,
    handleCreateQuiz,
    librarySearchQuery,
    setLibrarySearchQuery,
    librarySearchResult,
    librarySearchSuggestions,
    pickLibrarySearchSuggestion,
    getWordDetail,
  } = useAppStateContext()

  const [selectedDetail, setSelectedDetail] = useState<WordDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [showSuggestions, setShowSuggestions] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const searchBoxRef = useRef<HTMLLabelElement | null>(null)
  const isSearching = librarySearchQuery.trim().length > 0

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
      const detail = await getWordDetail(item.entryId)
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

  const onStartQuiz = () => void handleCreateQuiz()

  return (
    <>
      <div className="split">
        <div className="list">
          {wordbooks.map((book) => (
            <button
              key={book.id}
              type="button"
              className={book.id === selectedWordbookId ? 'book active' : 'book'}
              onClick={() => setSelectedWordbookId(book.id)}
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
          <label className="search-box" ref={searchBoxRef}>
            <span>搜索当前词书里的单词、释义或例句</span>
            <input
              value={librarySearchQuery}
              disabled={!selectedWordbookId}
              onChange={(event) => {
                setLibrarySearchQuery(event.target.value)
                setShowSuggestions(true)
              }}
              onFocus={() => {
                if (librarySearchQuery.trim()) {
                  setShowSuggestions(true)
                }
              }}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  setShowSuggestions(false)
                }
              }}
            />
            {showSuggestions && librarySearchQuery.trim() && librarySearchSuggestions.length > 0 && (
              <div className="suggestion-list">
                {librarySearchSuggestions.map((item) => (
                  <button
                    key={item.entryId}
                    type="button"
                    className="suggestion-item"
                    onClick={() => {
                      setShowSuggestions(false)
                      pickLibrarySearchSuggestion(item.word)
                    }}
                  >
                    <strong>{item.word}</strong>
                    <small>匹配 {item.matchPercent}%</small>
                  </button>
                ))}
              </div>
            )}
          </label>
          {isSearching ? (
            librarySearchResult.hits.length > 0 ? (
              librarySearchResult.hits.map((item) => (
                <SearchCard key={item.entryId} item={item} onOpen={openDetail} />
              ))
            ) : (
              <div className="meta">
                <span className="meta-label">词书内搜索</span>
                <span className="meta-value">当前词书里没有匹配这个关键词的词条。</span>
              </div>
            )
          ) : (
            entries.slice(0, 12).map((entry) => (
              <div key={entry.id} className="card">
                <strong>{entry.word}</strong>
                <span className="multiline-text">{formatMultilineText(entry.meaningCn)}</span>
                <small className="multiline-text">{formatMultilineText(entry.exampleSentence)}</small>
              </div>
            ))
          )}
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
