import { useEffect, useMemo, useRef, useState } from 'react'
import type { SearchHit, WordDetail } from '../api/modules/search'
import { useAppStateContext } from '../context/AppStateContext'
import { formatMultilineText } from '../utils/text'
import { SearchCard } from './SearchCard'
import { WordDetailModal } from './WordDetailModal'

export function LibraryView() {
  const {
    wordbooks,
    publicWordbooks,
    publicWordbookEntries,
    selectedPublicWordbookId,
    setSelectedPublicWordbookId,
    selectedPublicWordbook,
    subscribingPublicWordbookId,
    resettingPublicWordbookId,
    handleSubscribePublicWordbook,
    handleResetPublicWordbookProgress,
    selectedWordbookId,
    setSelectedWordbookId,
    selectedWordbook,
    wordbookProgress,
    entries,
    creatingQuiz,
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

  const subscribedPublicWordbooks = useMemo(
    () => publicWordbooks.filter((book) => book.subscribed),
    [publicWordbooks],
  )

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
      const detail = await getWordDetail(item.entryId, item.entryType)
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

  function onResetPublicWordbook() {
    if (!selectedPublicWordbookId) return
    if (!window.confirm('Reset progress and clear wrong words for this public wordbook?')) {
      return
    }
    void handleResetPublicWordbookProgress(selectedPublicWordbookId)
  }

  return (
    <>
      <div className="split">
        <div className="list">
          <div className="toolbar">
            <div>
              <p className="eyebrow">My imported wordbooks</p>
              <h4>{wordbooks.length} total</h4>
            </div>
          </div>
          {wordbooks.length > 0 ? (
            wordbooks.map((book) => (
              <button
                key={book.id}
                type="button"
                className={book.id === selectedWordbookId ? 'book active' : 'book'}
                onClick={() => setSelectedWordbookId(book.id)}
              >
                <strong>{book.name}</strong>
                <span>
                  {book.wordCount} words, cleared {book.clearedCount}
                </span>
              </button>
            ))
          ) : (
            <div className="meta">
              <span className="meta-label">Imports</span>
              <span className="meta-value">No imported wordbooks yet.</span>
            </div>
          )}

          <div className="public-wordbooks">
            <div className="toolbar public-wordbooks-head">
              <div>
                <p className="eyebrow">Subscribed public wordbooks</p>
                <h4>{subscribedPublicWordbooks.length} subscribed</h4>
              </div>
            </div>
            {subscribedPublicWordbooks.length > 0 ? (
              <div className="public-wordbook-grid">
                {subscribedPublicWordbooks.map((book) => (
                  <button
                    key={book.id}
                    type="button"
                    className={book.id === selectedPublicWordbookId ? 'book active' : 'book'}
                    onClick={() => setSelectedPublicWordbookId(book.id)}
                  >
                    <strong>{book.name}</strong>
                    <span>
                      {book.completedCount}/{book.wordCount} cleared, wrong words {book.wrongCount}
                    </span>
                  </button>
                ))}
              </div>
            ) : (
              <div className="meta">
                <span className="meta-label">Subscriptions</span>
                <span className="meta-value">Subscribe to a public wordbook to continue from saved progress.</span>
              </div>
            )}

            <div className="toolbar public-wordbooks-head">
              <div>
                <p className="eyebrow">Public wordbook catalog</p>
                <h4>{selectedPublicWordbook?.name ?? 'ECDICT'}</h4>
              </div>
              <button
                type="button"
                className="ghost"
                disabled={
                  !selectedPublicWordbookId ||
                  !!selectedPublicWordbook?.subscribed ||
                  subscribingPublicWordbookId != null
                }
                onClick={() => void handleSubscribePublicWordbook()}
              >
                {selectedPublicWordbook?.subscribed
                  ? 'Subscribed'
                  : subscribingPublicWordbookId === selectedPublicWordbookId
                    ? 'Subscribing...'
                    : 'Subscribe'}
              </button>
            </div>
            <div className="public-wordbook-grid">
              {publicWordbooks.map((book) => (
                <button
                  key={book.id}
                  type="button"
                  className={book.id === selectedPublicWordbookId ? 'book active' : 'book'}
                  onClick={() => setSelectedPublicWordbookId(book.id)}
                >
                  <strong>{book.name}</strong>
                  <span>
                    {book.wordCount} words | {book.subscribed ? 'Subscribed' : book.licenseName}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="list">
          <div className="toolbar">
            <div>
              <p className="eyebrow">Selected imported wordbook</p>
              <h4>{selectedWordbook?.name ?? 'None selected'}</h4>
            </div>
            <button
              type="button"
              className="primary"
              onClick={() => void handleCreateQuiz('USER_WORDBOOK')}
              disabled={creatingQuiz || !selectedWordbookId}
            >
              {creatingQuiz ? 'Creating...' : 'Start quiz'}
            </button>
          </div>

          {selectedWordbookId ? (
            <>
              {wordbookProgress && (
                <div className="meta">
                  <span className="meta-label">Progress</span>
                  <span className="meta-value">
                    Total {wordbookProgress.wordCount} / Cleared {wordbookProgress.clearedCount} / In progress{' '}
                    {wordbookProgress.inProgressCount}
                  </span>
                </div>
              )}
              <label className="search-box" ref={searchBoxRef}>
                <span>Search inside the selected imported wordbook</span>
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
                        key={`${item.entryType}-${item.entryId}`}
                        type="button"
                        className="suggestion-item"
                        onClick={() => {
                          setShowSuggestions(false)
                          pickLibrarySearchSuggestion(item.word)
                        }}
                      >
                        <strong>{item.word}</strong>
                        <small>match {item.matchPercent}%</small>
                      </button>
                    ))}
                  </div>
                )}
              </label>

              {isSearching ? (
                librarySearchResult.hits.length > 0 ? (
                  librarySearchResult.hits.map((item) => (
                    <SearchCard key={`${item.entryType}-${item.entryId}`} item={item} onOpen={openDetail} />
                  ))
                ) : (
                  <div className="meta">
                    <span className="meta-label">Search</span>
                    <span className="meta-value">No matching entries inside the selected wordbook.</span>
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
            </>
          ) : (
            <div className="meta">
              <span className="meta-label">Imported wordbooks</span>
              <span className="meta-value">Import a wordbook if you want a private list managed by yourself.</span>
            </div>
          )}

          <div className="public-wordbooks">
            <div className="toolbar public-wordbooks-head">
              <div>
                <p className="eyebrow">Selected public wordbook</p>
                <h4>{selectedPublicWordbook?.name ?? 'None selected'}</h4>
              </div>
              <div className="toolbar-actions">
                {selectedPublicWordbook?.subscribed ? (
                  <>
                    <button
                      type="button"
                      className="primary"
                      onClick={() => void handleCreateQuiz('PUBLIC_WORDBOOK')}
                      disabled={creatingQuiz || !selectedPublicWordbookId}
                    >
                      {creatingQuiz ? 'Creating...' : 'Continue'}
                    </button>
                    <button
                      type="button"
                      className="ghost"
                      onClick={onResetPublicWordbook}
                      disabled={!selectedPublicWordbookId || resettingPublicWordbookId != null}
                    >
                      {resettingPublicWordbookId === selectedPublicWordbookId ? 'Resetting...' : 'Reset progress'}
                    </button>
                  </>
                ) : (
                  <button
                    type="button"
                    className="ghost"
                    disabled={!selectedPublicWordbookId || subscribingPublicWordbookId != null}
                    onClick={() => void handleSubscribePublicWordbook()}
                  >
                    {subscribingPublicWordbookId === selectedPublicWordbookId ? 'Subscribing...' : 'Subscribe'}
                  </button>
                )}
              </div>
            </div>

            {selectedPublicWordbook ? (
              <>
                <div className="meta">
                  <span className="meta-label">Progress</span>
                  <span className="meta-value">
                    Total {selectedPublicWordbook.wordCount} / Cleared {selectedPublicWordbook.completedCount} / Wrong
                    words {selectedPublicWordbook.wrongCount} / Next #{selectedPublicWordbook.nextSortOrder}
                  </span>
                </div>
                {publicWordbookEntries.length > 0 && (
                  <div className="meta">
                    <span className="meta-label">Preview</span>
                    <span className="meta-value">
                      {publicWordbookEntries.slice(0, 8).map((entry) => entry.word).join(', ')}
                    </span>
                  </div>
                )}
                {publicWordbookEntries.slice(0, 12).map((entry) => (
                  <div key={entry.publicEntryId} className="card">
                    <strong>
                      #{entry.sortOrder} {entry.word}
                    </strong>
                    <span className="multiline-text">{formatMultilineText(entry.meaningCn)}</span>
                    <small className="multiline-text">{formatMultilineText(entry.exampleSentence)}</small>
                  </div>
                ))}
              </>
            ) : (
              <div className="meta">
                <span className="meta-label">Public wordbooks</span>
                <span className="meta-value">Choose a public wordbook to subscribe or continue studying.</span>
              </div>
            )}
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
