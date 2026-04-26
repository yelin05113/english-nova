import { useEffect, useRef, useState } from 'react'
import type { SearchHit, WordDetail } from '../api/modules/search'
import { useAppStateContext } from '../context/AppStateContext'
import { SearchCard } from './SearchCard'
import { WordDetailModal } from './WordDetailModal'

const AUDIO_GAIN_BOOST = 2.4
const BOOST_SAFE_AUDIO_HOSTS = new Set(['api.dictionaryapi.dev'])

export function SearchView() {
  const {
    searchQuery,
    setSearchQuery,
    searchResult,
    searchSuggestions,
    pickSearchSuggestion,
    getWordDetail,
  } = useAppStateContext()

  const [selectedDetail, setSelectedDetail] = useState<WordDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [showSuggestions, setShowSuggestions] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const audioSourceRef = useRef<MediaElementAudioSourceNode | null>(null)
  const audioGainRef = useRef<GainNode | null>(null)
  const searchBoxRef = useRef<HTMLLabelElement | null>(null)

  useEffect(() => {
    return () => {
      stopAudioPlayback()
      audioGainRef.current?.disconnect()
      audioSourceRef.current?.disconnect()
      void audioContextRef.current?.close()
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
    stopAudioPlayback()
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
    setSelectedDetail(null)
  }

  function stopAudioPlayback() {
    audioRef.current?.pause()
    if (audioRef.current) {
      audioRef.current.currentTime = 0
    }
    audioRef.current = null
    audioSourceRef.current?.disconnect()
    audioGainRef.current?.disconnect()
    audioSourceRef.current = null
    audioGainRef.current = null
  }

  async function connectBoostedAudio(audio: HTMLAudioElement) {
    const AudioContextCtor = window.AudioContext
    if (!AudioContextCtor) {
      return
    }

    const context = audioContextRef.current ?? new AudioContextCtor()
    audioContextRef.current = context
    if (context.state === 'suspended') {
      await context.resume()
    }

    const source = context.createMediaElementSource(audio)
    const gain = context.createGain()
    gain.gain.value = AUDIO_GAIN_BOOST
    source.connect(gain)
    gain.connect(context.destination)
    audioSourceRef.current = source
    audioGainRef.current = gain
  }

  function canUseBoostedAudio(audioUrl: string) {
    try {
      const resolvedUrl = new URL(audioUrl, window.location.href)
      return resolvedUrl.origin === window.location.origin || BOOST_SAFE_AUDIO_HOSTS.has(resolvedUrl.hostname)
    } catch {
      return false
    }
  }

  async function playAudio(detail: WordDetail) {
    stopAudioPlayback()

    if (detail.audioUrl) {
      const audio = new Audio()
      const useBoostedAudio = canUseBoostedAudio(detail.audioUrl)
      if (useBoostedAudio) {
        audio.crossOrigin = 'anonymous'
      }
      audio.src = detail.audioUrl
      audio.volume = 1
      audio.preload = 'auto'
      audioRef.current = audio
      if (useBoostedAudio) {
        try {
          await connectBoostedAudio(audio)
        } catch {
          audioSourceRef.current = null
          audioGainRef.current = null
        }
      }
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
    utterance.volume = 1
    window.speechSynthesis.speak(utterance)
  }

  return (
    <>
      <div className="list">
        <label className="search-box" ref={searchBoxRef}>
          <span>搜索英文单词、中文释义或例句</span>
          <input
            id="global-search-query"
            name="query"
            value={searchQuery}
            onChange={(event) => {
              setSearchQuery(event.target.value)
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
          {showSuggestions && searchQuery.trim() && searchSuggestions.length > 0 && (
            <div className="suggestion-list">
              {searchSuggestions.map((item) => (
                <button
                  key={`${item.entryType}-${item.entryId}`}
                  type="button"
                  className="suggestion-item"
                  onClick={() => {
                    setShowSuggestions(false)
                    pickSearchSuggestion(item.word)
                  }}
                >
                  <strong>{item.word}</strong>
                  <small>匹配 {item.matchPercent}%</small>
                </button>
              ))}
            </div>
          )}
        </label>
        <div className="list">
          {searchResult.hits.map((item) => (
            <SearchCard key={`${item.entryType}-${item.entryId}`} item={item} onOpen={openDetail} />
          ))}
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
