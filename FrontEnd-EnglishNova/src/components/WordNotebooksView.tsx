import { useEffect, useMemo, useRef, useState } from 'react'
import { searchApi, type WordDetail } from '../api/modules/search'
import type { SearchEntryType } from '../api/modules/search'
import type { WordNotebookEntry } from '../api/modules/wordNotebooks'
import { useAppStateContext } from '../context/AppStateContext'
import { WordDetailModal } from './WordDetailModal'

const AUDIO_GAIN_BOOST = 2.4
const BOOST_SAFE_AUDIO_HOSTS = new Set(['api.dictionaryapi.dev'])

export function WordNotebooksView() {
  const {
    token,
    openAuthModal,
    showGlobalNotice,
    wordNotebooks,
    selectedWordNotebookId,
    selectedWordNotebook,
    setSelectedWordNotebookId,
    wordNotebookEntries,
    wordNotebookEntriesLoading,
    handleCreateWordNotebook,
    handleCreateQuiz,
    quizState,
    getWordDetail,
  } = useAppStateContext()
  const [draftName, setDraftName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [notebookModalOpen, setNotebookModalOpen] = useState(false)
  const [selectedEntry, setSelectedEntry] = useState<WordNotebookEntry | null>(null)
  const [selectedDetail, setSelectedDetail] = useState<WordDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const audioSourceRef = useRef<MediaElementAudioSourceNode | null>(null)
  const audioGainRef = useRef<GainNode | null>(null)

  const totalCollectedWords = useMemo(
    () => wordNotebooks.reduce((total, notebook) => total + notebook.wordCount, 0),
    [wordNotebooks],
  )
  const learningNotebookId = quizState?.session.targetType === 'WORD_NOTEBOOK' ? quizState.session.targetId : null

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

  async function submitCreateNotebook() {
    const name = draftName.trim()
    if (!name || submitting) {
      return
    }
    setSubmitting(true)
    const created = await handleCreateWordNotebook(name)
    setSubmitting(false)
    if (created) {
      setDraftName('')
    }
  }

  function selectNotebook(notebookId: number) {
    setSelectedWordNotebookId(notebookId)
  }

  function viewNotebook(notebookId: number) {
    const notebook = wordNotebooks.find((item) => item.id === notebookId) ?? null
    setSelectedWordNotebookId(notebookId)
    if (notebook && notebook.wordCount <= 0) {
      showGlobalNotice('当前单词本还没有单词，请先收藏单词后再查看。', 'error')
      return
    }
    setNotebookModalOpen(true)
  }

  async function startNotebookQuiz(notebookId: number) {
    const notebook = wordNotebooks.find((item) => item.id === notebookId) ?? null
    setSelectedWordNotebookId(notebookId)
    if (notebook && notebook.wordCount <= 0) {
      showGlobalNotice('当前单词本还没有单词，请先收藏单词后再背词。', 'error')
      return
    }
    await handleCreateQuiz('WORD_NOTEBOOK', notebookId)
  }

  async function openWordDetail(entry: WordNotebookEntry) {
    if (!token) {
      openAuthModal()
      return
    }

    setSelectedEntry(entry)
    setSelectedDetail(null)
    setDetailLoading(true)
    try {
      const detail = await resolveWordDetail(entry)
      if (!detail) {
        return
      }
      setSelectedDetail(detail)
      await playAudio(detail)
    } finally {
      setDetailLoading(false)
    }
  }

  async function resolveWordDetail(entry: WordNotebookEntry) {
    if (entry.sourceEntryId != null) {
      return getWordDetail(entry.sourceEntryId, (entry.sourceEntryType ?? 'PUBLIC') as SearchEntryType)
    }

    return searchApi.findWordDetailByWord(
      entry.word,
      {
        token,
        onUnauthorized: () => openAuthModal(),
      },
      entry.sourceEntryType ?? undefined,
    )
  }

  function closeDetail() {
    stopAudioPlayback()
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
    setSelectedEntry(null)
    setSelectedDetail(null)
    setDetailLoading(false)
  }

  function closeNotebookModal() {
    setNotebookModalOpen(false)
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
    await playAudioSource(detail.audioUrl, detail.word)
  }

  async function playAudioSource(audioUrl: string, fallbackText: string) {
    stopAudioPlayback()

    if (audioUrl) {
      const audio = new Audio()
      const useBoostedAudio = canUseBoostedAudio(audioUrl)
      if (useBoostedAudio) {
        audio.crossOrigin = 'anonymous'
      }
      audio.src = audioUrl
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
      void audio.play().catch(() => speak(fallbackText))
      return
    }

    speak(fallbackText)
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
      <div className="word-notebooks-page">
        <section className="word-notebooks-hero">
          <div className="word-notebooks-hero-copy">
            <p className="eyebrow">单词本</p>
            <h2>你的收藏单词夹</h2>
            <p>把练习中想反复记住的单词单独收纳起来。每个单词本互相独立，同一个单词在同一册里不会重复保存。</p>
          </div>
          <div className="word-notebooks-hero-stats" aria-label="单词本概览">
            <article>
              <span>单词本</span>
              <strong>{wordNotebooks.length}</strong>
            </article>
            <article>
              <span>已收藏单词</span>
              <strong>{totalCollectedWords}</strong>
            </article>
          </div>
        </section>

        <section className="word-notebooks-toolbar">
          <div>
            <p className="eyebrow">新建单词本</p>
            <h3>按主题整理你的收藏</h3>
          </div>
          <div className="word-notebooks-create">
            <input
              value={draftName}
              onChange={(event) => setDraftName(event.target.value)}
              placeholder="例如：连接词 / 面试高频 / 易混词"
              maxLength={120}
            />
            <button
              type="button"
              className="primary"
              disabled={submitting || !draftName.trim()}
              onClick={() => void submitCreateNotebook()}
            >
              {submitting ? '创建中...' : '创建单词本'}
            </button>
          </div>
        </section>

        <section className="word-notebooks-sidebar word-notebooks-sidebar--full">
          <div className="word-notebooks-sidebar-head">
            <h3>单词本列表</h3>
            <span>{wordNotebooks.length} 本</span>
          </div>
          {wordNotebooks.length > 0 ? (
            <div className="word-notebooks-list" role="list" aria-label="单词本列表">
              {wordNotebooks.map((notebook) => {
                const isSelected = notebook.id === selectedWordNotebookId
                const isLearning = learningNotebookId === notebook.id

                return (
                  <article key={notebook.id} role="listitem" className={`word-notebook-card${isSelected ? ' active' : ''}`}>
                    <div className="word-notebook-card-top">
                      <button
                        type="button"
                        className="word-notebook-card-main"
                        onClick={() => selectNotebook(notebook.id)}
                      >
                        <span className="word-notebook-card-title">
                          <strong>{notebook.name}</strong>
                          {isLearning ? <em className="word-notebook-status-pill">正在学习</em> : null}
                        </span>
                        <span>{notebook.wordCount} 个单词</span>
                      </button>
                      <div className="word-notebook-card-actions">
                        <button
                          type="button"
                          className="ghost word-notebook-view-button"
                          onClick={() => viewNotebook(notebook.id)}
                        >
                          {isSelected ? '当前查看' : '查看'}
                        </button>
                        <button
                          type="button"
                          className="word-notebook-learn-button"
                          onClick={() => void startNotebookQuiz(notebook.id)}
                        >
                          背词
                        </button>
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>
          ) : (
            <div className="word-notebooks-empty">
              <strong>还没有单词本</strong>
              <span>先创建一个单词本，再从背词页的小星星把单词收进去。</span>
            </div>
          )}
        </section>
      </div>

      {notebookModalOpen && selectedWordNotebook ? (
        <div className="modal-backdrop" role="presentation" onClick={closeNotebookModal}>
          <section className="modal-card word-notebook-detail-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="panel-head detail-head">
              <div className="detail-title-block">
                <div className="detail-title-row">
                  <h3>{selectedWordNotebook.name}</h3>
                  <strong className="phonetic-text detail-phonetic">{selectedWordNotebook.wordCount} 个单词</strong>
                </div>
              </div>
              <button type="button" className="ghost detail-close-button" onClick={closeNotebookModal}>
                关闭
              </button>
            </div>
            {wordNotebookEntriesLoading ? (
              <div className="word-notebook-detail-loading">
                <strong>正在加载单词列表</strong>
                <span>请稍等一下。</span>
              </div>
            ) : wordNotebookEntries.length > 0 ? (
              <div className="word-notebook-word-list" role="list" aria-label={`${selectedWordNotebook.name} 单词列表`}>
                {wordNotebookEntries.map((entry) => (
                  <button
                    key={entry.id}
                    type="button"
                    role="listitem"
                    className="word-notebook-word-button"
                    onClick={() => void openWordDetail(entry)}
                  >
                    <strong>{entry.word}</strong>
                  </button>
                ))}
              </div>
            ) : (
              <div className="word-notebooks-empty word-notebooks-detail-empty">
                <strong>这个单词本还是空的</strong>
                <span>先去背词页收藏一些单词，再回来查看这里的列表。</span>
              </div>
            )}
          </section>
        </div>
      ) : null}

      {selectedDetail ? (
        <WordDetailModal
          detail={selectedDetail}
          loading={detailLoading}
          onClose={closeDetail}
          onReplayAudio={() => void playAudio(selectedDetail)}
          onReplayExampleAudio={() =>
            void playAudioSource(selectedDetail.exampleAudioUrl, selectedDetail.correctedExampleSentence)
          }
        />
      ) : detailLoading && selectedEntry ? (
        <div className="modal-backdrop" role="presentation" onClick={closeDetail}>
          <section className="modal-card" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="panel-head detail-head">
              <div className="detail-title-block">
                <div className="detail-title-row">
                  <h3>{selectedEntry.word}</h3>
                </div>
              </div>
              <button type="button" className="ghost detail-close-button" onClick={closeDetail}>
                关闭
              </button>
            </div>
            <div className="word-notebook-detail-loading">
              <strong>正在加载单词详情</strong>
              <span>请稍等一下。</span>
            </div>
          </section>
        </div>
      ) : null}
    </>
  )
}
