import { useCallback, useEffect, useRef, useState, type CSSProperties, type MouseEvent as ReactMouseEvent } from 'react'
import { Link } from 'react-router'
import { useAppStateContext } from '../context/AppStateContext'
import type { PublicWordbookProgressSnapshot, QuizAnswerResult, QuizOptionDetail, QuizOptionStrategy } from '../api/modules/quiz'
import { searchApi, type WordDetail } from '../api/modules/search'
import { wordNotebookApi, type WordNotebookSummary } from '../api/modules/wordNotebooks'
import { formatMultilineText } from '../utils/text'
import { getWordbookArtwork, type WordbookArtworkKind } from '../utils/wordbookArtwork'
import { DailyTargetModal, isDailyQuotaCompleted } from './DailyTargetModal'
import { QuizAiAssistant } from './QuizAiAssistant'

interface OptionState {
  option: string
  status: 'correct' | 'wrong' | 'idle'
}

interface Feedback {
  correct: boolean
  correctOption: string
  dailyTargetJustCompleted: boolean
}

interface MeaningLine {
  partOfSpeech: string
  meaning: string
}

interface ExampleSentenceToken {
  type: 'word' | 'separator'
  value: string
  normalized?: string
}

interface ExampleLookupPosition {
  top: number
  left: number
  centered: boolean
  placement: 'top' | 'bottom'
}

interface ExampleLookupPopoverState {
  key: string
  word: string
  displayWord: string
  position: ExampleLookupPosition
}

interface WordNotebookDraftEntry {
  word: string
  phonetic: string
  meaningCn: string
  exampleSentence: string
  correctedExampleSentence: string
  chineseSentence: string
  exampleAudioUrl: string
  optionDetails: QuizOptionDetail[]
  correctOption: string
  sourceEntryType: 'PUBLIC' | 'USER' | null
  sourceEntryId: number | null
}

type ExampleLookupCacheEntry =
  | { status: 'loading' }
  | { status: 'empty' }
  | { status: 'error'; message: string }
  | { status: 'success'; detail: WordDetail }

const PART_OF_SPEECH_PATTERN = /(^|[\s\n])((?:abbr|adj|adv|aux|conj|int|num|prep|pron|vt|vi|art|pl|n|v|a)\.)\s*/gi
const EXAMPLE_SENTENCE_WORD_PATTERN = /[A-Za-z]+(?:[\'鈥?][A-Za-z]+)*/g
const QUIZ_AUDIO_VOLUME = 1
const QUIZ_AUDIO_GAIN_BOOST = 2.4
const BOOST_SAFE_AUDIO_HOSTS = new Set(['api.dictionaryapi.dev'])

function resolveNotebookCorrectOption(question: {
  currentWord: string
  meaningCn: string
  optionDetails: QuizOptionDetail[]
}) {
  const currentWord = (question.currentWord || '').trim().toLowerCase()
  const meaningCn = (question.meaningCn || '').trim()

  const byWord = question.optionDetails.find((detail) => (detail.word || '').trim().toLowerCase() === currentWord)
  if (byWord?.value) {
    return byWord.value
  }

  const byMeaning = question.optionDetails.find(
    (detail) => (detail.value || '').trim() === meaningCn || (detail.meaningCn || '').trim() === meaningCn,
  )
  return byMeaning?.value || ''
}

function QuizBookArtwork({
  name,
  kind,
}: {
  name: string
  kind: WordbookArtworkKind
}) {
  const src = getWordbookArtwork(name, kind)
  const [imageError, setImageError] = useState(false)

  if (!src || imageError) {
    return (
      <div className="quiz-book-cover is-fallback" aria-hidden="true">
        <span>{name.charAt(0).toUpperCase()}</span>
      </div>
    )
  }

  return (
    <div className="quiz-book-cover has-artwork" aria-hidden="true">
      <img src={src} alt="" loading="lazy" onError={() => setImageError(true)} />
    </div>
  )
}

function tokenizeExampleSentence(sentence: string): ExampleSentenceToken[] {
  if (!sentence) {
    return []
  }

  const tokens: ExampleSentenceToken[] = []
  let cursor = 0
  for (const match of sentence.matchAll(EXAMPLE_SENTENCE_WORD_PATTERN)) {
    const value = match[0]
    const index = match.index ?? 0
    if (index > cursor) {
      tokens.push({ type: 'separator', value: sentence.slice(cursor, index) })
    }
    tokens.push({
      type: 'word',
      value,
      normalized: normalizeLookupWord(value),
    })
    cursor = index + value.length
  }

  if (cursor < sentence.length) {
    tokens.push({ type: 'separator', value: sentence.slice(cursor) })
  }
  return tokens
}

function normalizeLookupWord(token: string) {
  return token
    .trim()
    .replace(/^[^A-Za-z]+|[^A-Za-z]+$/g, '')
    .toLowerCase()
}

const QUIZ_OPTION_STRATEGIES: Array<{
  value: QuizOptionStrategy
  label: string
  tooltip: string
}> = [
  {
    value: 'SIMILAR',
    label: '相似选项',
    tooltip: '选项会和当前单词在长度或词形上更接近',
  },
  {
    value: 'RELATED',
    label: '关联选项',
    tooltip: '选项会和当前单词在含义上更接近',
  },
  {
    value: 'RANDOM',
    label: '随机选项',
    tooltip: '选项会随机分配',
  },
]

export function QuizView() {
  const {
    token,
    clearAuth,
    agenda: rawAgenda,
    progress,
    publicWordbooks,
    wordNotebooks,
    selectedWordbook,
    selectedPublicWordbook,
    selectedWordNotebook,
    setSelectedPublicWordbookId,
    setSelectedWordNotebookId,
    quizState,
    creatingQuiz,
    quizOptionStrategy,
    handleUpdateQuizOptionStrategy,
    handleUpdatePublicWordbookDailyTarget,
    handleCreateQuiz,
    handleResetPublicWordbookProgress,
    handleAnswer,
    openAuthModal,
    showGlobalNotice,
    handleCreateWordNotebook,
    handleAddWordNotebookEntry,
    handleRemoveWordNotebookEntry,
    advanceQuiz,
  } = useAppStateContext()
  const [optionStates, setOptionStates] = useState<OptionState[]>([])
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [dailyCompletion, setDailyCompletion] = useState<PublicWordbookProgressSnapshot | null>(null)
  const [dailyTargetModalBookId, setDailyTargetModalBookId] = useState<number | null>(null)
  const [savingDailyTarget, setSavingDailyTarget] = useState<number | null>(null)
  const [pendingAdvance, setPendingAdvance] = useState<QuizAnswerResult | null>(null)
  const [latestAnswerResult, setLatestAnswerResult] = useState<QuizAnswerResult | null>(null)
  const [localFirstChoiceStats, setLocalFirstChoiceStats] = useState({ correct: 0, total: 0 })
  const [firstChoiceBaseline, setFirstChoiceBaseline] = useState({ correct: 0, total: 0 })
  const [optionStrategyOpen, setOptionStrategyOpen] = useState(false)
  const [exampleLookupCache, setExampleLookupCache] = useState<Record<string, ExampleLookupCacheEntry>>({})
  const [exampleLookupPopover, setExampleLookupPopover] = useState<ExampleLookupPopoverState | null>(null)
  const [currentWordNotebookOptions, setCurrentWordNotebookOptions] = useState<WordNotebookSummary[]>([])
  const [loadingCurrentWordNotebookOptions, setLoadingCurrentWordNotebookOptions] = useState(false)
  const [wordNotebookModalOpen, setWordNotebookModalOpen] = useState(false)
  const [wordNotebookDraftName, setWordNotebookDraftName] = useState('')
  const [wordNotebookSubmitting, setWordNotebookSubmitting] = useState(false)
  const feedbackRef = useRef<HTMLDivElement>(null)
  const fullMeaningRef = useRef<HTMLDivElement>(null)
  const answerDetailRef = useRef<HTMLDivElement>(null)
  const exampleLookupPopoverRef = useRef<HTMLDivElement>(null)
  const activeExampleLookupAnchorRef = useRef<HTMLButtonElement | null>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const audioSourceRef = useRef<MediaElementAudioSourceNode | null>(null)
  const audioGainRef = useRef<GainNode | null>(null)
  const autoPlayedExampleQuestionIdRef = useRef<number | null>(null)
  const prevQuestionId = useRef<number | null>(null)
  const currentNotebookLookupWordRef = useRef('')

  const question = quizState?.currentQuestion
  const isPublicQuiz = quizState?.session.targetType === 'PUBLIC_WORDBOOK'
  const notebookLookupWord = (question?.currentWord || question?.promptText || '').trim()
  const activeSessionForStrategy = latestAnswerResult?.session ?? quizState?.session
  const effectiveQuizOptionStrategy: QuizOptionStrategy = activeSessionForStrategy?.quizOptionStrategy ?? quizOptionStrategy
  const selectedOptionStrategy =
    QUIZ_OPTION_STRATEGIES.find((strategy) => strategy.value === effectiveQuizOptionStrategy) ?? QUIZ_OPTION_STRATEGIES[2]

  const loadCurrentWordNotebookOptions = useCallback(
    async (word: string) => {
      if (!token || !word.trim()) {
        currentNotebookLookupWordRef.current = ''
        setCurrentWordNotebookOptions([])
        return []
      }

      const lookupWord = word.trim().toLowerCase()
      currentNotebookLookupWordRef.current = lookupWord
      setLoadingCurrentWordNotebookOptions(true)
      try {
        const notebooks = await wordNotebookApi.listWordNotebooks({ token, onUnauthorized: clearAuth }, word)
        if (currentNotebookLookupWordRef.current === lookupWord) {
          setCurrentWordNotebookOptions(notebooks)
        }
        return notebooks
      } catch {
        if (currentNotebookLookupWordRef.current === lookupWord) {
          setCurrentWordNotebookOptions([])
        }
        return []
      } finally {
        if (currentNotebookLookupWordRef.current === lookupWord) {
          setLoadingCurrentWordNotebookOptions(false)
        }
      }
    },
    [clearAuth, token],
  )

  const resolveCurrentWordNotebookEntry = useCallback(async (): Promise<WordNotebookDraftEntry | null> => {
    if (!question) {
      return null
    }

    const word = (question.currentWord || question.promptText || '').trim()
    if (!word) {
      return null
    }

    return {
      word,
      phonetic: question.phonetic || '',
      meaningCn: question.meaningCn || '',
      exampleSentence: question.exampleSentence || '',
      correctedExampleSentence: question.correctedExampleSentence || '',
      chineseSentence: question.chineseSentence || '',
      exampleAudioUrl: question.exampleAudioUrl || '',
      optionDetails: question.optionDetails?.length === 4 ? question.optionDetails : [],
      correctOption: question.optionDetails?.length === 4 ? resolveNotebookCorrectOption(question) : '',
      sourceEntryType: question.sourceEntryType,
      sourceEntryId: question.sourceEntryId,
    }
  }, [question])

  useEffect(() => {
    setFirstChoiceBaseline({
      correct: quizState?.session.todayCorrectAttempts ?? 0,
      total: quizState?.session.todayTotalAttempts ?? 0,
    })
    setLocalFirstChoiceStats({ correct: 0, total: 0 })
  }, [quizState?.session.id])

  useEffect(() => {
    const currentId = question?.attemptId ?? null
    const nextOptionStates = (question?.options ?? []).map((option) => ({
      option,
      status: 'idle' as const,
    }))
    if (currentId !== prevQuestionId.current) {
      prevQuestionId.current = currentId
      // Reset answer UI when the backend advances to a different question.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFeedback(null)
      setPendingAdvance(null)
        setLatestAnswerResult(null)
        setSubmitting(false)
        setOptionStates(nextOptionStates)
        setExampleLookupCache({})
        setExampleLookupPopover(null)
        setCurrentWordNotebookOptions([])
        setWordNotebookModalOpen(false)
        setWordNotebookDraftName('')
        setWordNotebookSubmitting(false)
        currentNotebookLookupWordRef.current = ''
        activeExampleLookupAnchorRef.current = null

      if (currentId != null) {
        setDailyCompletion(null)
      }
      autoPlayedExampleQuestionIdRef.current = null
      return
    }

    if (!feedback) {
      setOptionStates((current) => {
        const alreadySynced =
          current.length === nextOptionStates.length &&
          current.every((entry, index) => entry.option === nextOptionStates[index]?.option && entry.status === 'idle')
        return alreadySynced ? current : nextOptionStates
      })
    }
  }, [feedback, question?.attemptId, question?.options])

  useEffect(() => {
    if (!token || !isPublicQuiz || !notebookLookupWord) {
      setCurrentWordNotebookOptions([])
      return
    }
    void loadCurrentWordNotebookOptions(notebookLookupWord)
  }, [isPublicQuiz, loadCurrentWordNotebookOptions, notebookLookupWord, question?.attemptId, token])

  function onCreateQuiz() {
    return handleCreateQuiz(quizState?.session.targetType ?? 'USER_WORDBOOK', quizState?.session.targetId)
  }

  function startPublicWordbookQuiz(publicWordbookId: number) {
    setSelectedPublicWordbookId(publicWordbookId)
    setPendingAdvance(null)
    setLatestAnswerResult(null)
    void handleCreateQuiz('PUBLIC_WORDBOOK', publicWordbookId)
  }

  function startWordNotebookQuiz(notebookId: number) {
    const notebook = wordNotebooks.find((item) => item.id === notebookId) ?? null
    setSelectedWordNotebookId(notebookId)
    setPendingAdvance(null)
    setLatestAnswerResult(null)
    if (notebook && notebook.wordCount <= 0) {
      showGlobalNotice('当前单词本还没有单词，请先收藏单词后再背词。', 'error')
      return
    }
    void handleCreateQuiz('WORD_NOTEBOOK', notebookId)
  }

  function selectOptionStrategy(strategy: QuizOptionStrategy) {
    setOptionStrategyOpen(false)
    if (strategy === quizOptionStrategy) {
      return
    }
    void handleUpdateQuizOptionStrategy(strategy, feedback ? undefined : question?.attemptId)
  }

  function triggerDailyCompletion(snapshot: PublicWordbookProgressSnapshot | null) {
    if (!snapshot) {
      return
    }

    setDailyCompletion(snapshot)
  }

  useEffect(() => {
    return () => {
      stopPromptAudio()
      audioGainRef.current?.disconnect()
      audioSourceRef.current?.disconnect()
      void audioContextRef.current?.close()
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel()
      }
    }
  }, [])

  const stopPromptAudio = useCallback(() => {
    audioRef.current?.pause()
    if (audioRef.current) {
      audioRef.current.currentTime = 0
    }
    audioRef.current = null
    audioSourceRef.current?.disconnect()
    audioGainRef.current?.disconnect()
    audioSourceRef.current = null
    audioGainRef.current = null
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
  }, [])

  const speakPrompt = useCallback((word: string) => {
    if (!('speechSynthesis' in window)) {
      return
    }
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(word)
    utterance.lang = 'en-US'
    utterance.volume = QUIZ_AUDIO_VOLUME
    window.speechSynthesis.speak(utterance)
  }, [])

  const connectBoostedAudio = useCallback(async (audio: HTMLAudioElement) => {
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
    gain.gain.value = QUIZ_AUDIO_GAIN_BOOST
    source.connect(gain)
    gain.connect(context.destination)
    audioSourceRef.current = source
    audioGainRef.current = gain
  }, [])

  const canUseBoostedAudio = useCallback((audioUrl: string) => {
    try {
      const resolvedUrl = new URL(audioUrl, window.location.href)
      return resolvedUrl.origin === window.location.origin || BOOST_SAFE_AUDIO_HOSTS.has(resolvedUrl.hostname)
    } catch {
      return false
    }
  }, [])

  const playPromptAudio = useCallback(
    async (audioUrl: string, word: string) => {
      stopPromptAudio()
      if (audioUrl) {
        const audio = new Audio()
        const useBoostedAudio = canUseBoostedAudio(audioUrl)
        if (useBoostedAudio) {
          audio.crossOrigin = 'anonymous'
        }
        audio.src = audioUrl
        audio.volume = QUIZ_AUDIO_VOLUME
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
        void audio.play().catch(() => speakPrompt(word))
        return
      }
      speakPrompt(word)
    },
    [canUseBoostedAudio, connectBoostedAudio, speakPrompt, stopPromptAudio],
  )

  useEffect(() => {
    if (!isPublicQuiz || !question) {
      return
    }

    const timer = window.setTimeout(() => {
      playPromptAudio(question.audioUrl, question.promptText)
    }, 80)

    return () => {
      window.clearTimeout(timer)
      stopPromptAudio()
    }
  }, [isPublicQuiz, playPromptAudio, question?.attemptId, question?.audioUrl, question?.promptText, stopPromptAudio])

  function replayCurrentWord() {
    if ((!isPublicQuiz && !isNotebookQuiz) || !question) {
      return
    }
    playPromptAudio(question.audioUrl, question.promptText)
  }

  const replayExampleSentence = useCallback(
    (audioUrl: string, sentence: string) => {
      if (!sentence.trim()) {
        return
      }
      playPromptAudio(audioUrl, sentence)
    },
    [playPromptAudio],
  )

  const updateExampleLookupPosition = useCallback((anchor: HTMLElement): ExampleLookupPosition => {
    const rect = anchor.getBoundingClientRect()
    const viewportWidth = window.innerWidth || document.documentElement.clientWidth
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight
    const centered = viewportWidth <= 640
    const cardWidth = Math.min(280, viewportWidth - 24)

    if (centered) {
      return {
        top: Math.max(16, Math.min(viewportHeight - 220, viewportHeight * 0.28)),
        left: Math.max(12, (viewportWidth - cardWidth) / 2),
        centered: true,
        placement: 'bottom',
      }
    }

    const bottomTop = rect.bottom + 12
    const topTop = rect.top - 160
    const placement = bottomTop + 168 <= viewportHeight || topTop < 16 ? 'bottom' : 'top'
    const top = placement === 'bottom' ? bottomTop : Math.max(16, topTop)
    const left = Math.min(
      Math.max(12, rect.left + rect.width / 2 - cardWidth / 2),
      Math.max(12, viewportWidth - cardWidth - 12),
    )

    return { top, left, centered: false, placement }
  }, [])

  const loadExampleLookupDetail = useCallback(
    async (normalizedWord: string, cacheKey: string) => {
      if (!normalizedWord || !cacheKey) {
        return
      }

      setExampleLookupCache((current) => ({ ...current, [cacheKey]: { status: 'loading' } }))
      try {
        const detail = await searchApi.findWordDetailByWord(normalizedWord, { token, onUnauthorized: clearAuth }, 'PUBLIC')
        if (!detail) {
          setExampleLookupCache((current) => ({ ...current, [cacheKey]: { status: 'empty' } }))
          return
        }
        setExampleLookupCache((current) => ({ ...current, [cacheKey]: { status: 'success', detail } }))
      } catch (error) {
        setExampleLookupCache((current) => ({
          ...current,
          [cacheKey]: {
            status: 'error',
            message: error instanceof Error ? error.message : '查词失败',
          },
        }))
      }
    },
    [clearAuth, token],
  )

  const scrollAnsweredContentIntoView = useCallback(() => {
    const fullMeaningElement = fullMeaningRef.current
    const exampleElement = answerDetailRef.current
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight

    if (fullMeaningElement) {
      const fullMeaningTop = window.scrollY + fullMeaningElement.getBoundingClientRect().top
      const targetTop = Math.max(0, fullMeaningTop - viewportHeight * 0.18)
      window.scrollTo({ top: targetTop, behavior: 'smooth' })
      return
    }

    if (exampleElement) {
      const exampleRect = exampleElement.getBoundingClientRect()
      const exampleTop = window.scrollY + exampleRect.top
      const targetTop = Math.max(0, exampleTop - viewportHeight * 0.3)
      window.scrollTo({ top: targetTop, behavior: 'smooth' })
    }
  }, [])

  function splitMeaningItems(value: string) {
    return value
      .replace(/\\n/g, '\n')
      .replace(/\s+/g, ' ')
      .split(/[;；。。，,、/]+/)
      .map((part) => part.trim())
      .filter(Boolean)
  }

  function toPartOfSpeechMeaningLines(value: string): MeaningLine[] {
    const normalized = value.replace(/\\n/g, '\n').trim()
    if (!normalized) return []

    const matches = Array.from(normalized.matchAll(PART_OF_SPEECH_PATTERN))
    if (matches.length === 0) {
      const fallback = splitMeaningItems(normalized).join('、')
      return fallback ? [{ partOfSpeech: '', meaning: fallback }] : []
    }

    const grouped = new Map<string, string[]>()
    matches.forEach((match, index) => {
      const partOfSpeech = match[2].toLowerCase()
      const contentStart = match.index + match[0].length
      const contentEnd = matches[index + 1]?.index ?? normalized.length
      const items = splitMeaningItems(normalized.slice(contentStart, contentEnd))
      if (items.length === 0) {
        return
      }
      grouped.set(partOfSpeech, [...(grouped.get(partOfSpeech) ?? []), ...items])
    })

    return Array.from(grouped, ([partOfSpeech, meanings]) => ({
      partOfSpeech,
      meaning: meanings.join('、'),
    }))
  }

  function toPrimaryMeaning(value: string) {
    const primaryMeanings = toPartOfSpeechMeaningLines(value)
      .flatMap((line) => line.meaning.split('、'))
      .map((part) => part.trim())
      .filter(Boolean)
      .slice(0, 2)

    const summary = primaryMeanings.length > 0 ? primaryMeanings.join('、') : value.replace(/\s+/g, ' ').trim()
    return summary.slice(0, 64)
  }

  function toSinglePrimaryMeaning(value: string) {
    const primaryMeaning = toPartOfSpeechMeaningLines(value)
      .flatMap((line) => line.meaning.split('、'))
      .map((part) => part.trim())
      .find(Boolean)

    const summary = primaryMeaning ?? value.replace(/\s+/g, ' ').trim()
    return summary.slice(0, 42)
  }

  function getOptionDetail(option: string): QuizOptionDetail | null {
    return question?.optionDetails?.find((detail) => detail.value === option) ?? null
  }

  function renderOptionText(option: string, status: OptionState['status']) {
    if (!showMeaningOptions || status === 'idle') {
      return <span className="option-text">{showMeaningOptions ? toPrimaryMeaning(option) : option}</span>
    }

    const detail = getOptionDetail(option)
    return (
      <span className="option-text option-text--revealed">
        <strong>{detail?.word || question?.promptText || '-'}</strong>
        <small>{toSinglePrimaryMeaning(detail?.meaningCn || option)}</small>
      </span>
    )
  }

  function handleNextQuestion() {
    if (!pendingAdvance) {
      return
    }
    void advanceQuiz(pendingAdvance)
    setPendingAdvance(null)
  }

  function handleResetProgress() {
    if (!isPublicQuiz || !quizState?.session.targetId) {
      return
    }
    setPendingAdvance(null)
    void handleResetPublicWordbookProgress(quizState.session.targetId)
  }

  function openDailyTargetModal() {
    const targetBook = (activePublicWordbook?.subscribed ? activePublicWordbook : null) ?? subscribedPublicWordbooks[0] ?? null
    if (!targetBook) {
      return
    }
    setSelectedPublicWordbookId(targetBook.id)
    setDailyTargetModalBookId(targetBook.id)
  }

  function closeDailyTargetModal() {
    if (savingDailyTarget != null) {
      return
    }
    setDailyTargetModalBookId(null)
  }

  async function updateDailyTarget(target: number) {
    if (!dailyTargetModalBook) {
      return
    }
    setSavingDailyTarget(target)
    const updated = await handleUpdatePublicWordbookDailyTarget(dailyTargetModalBook.id, target)
    setSavingDailyTarget(null)
    if (updated) {
      closeDailyTargetModal()
    }
  }

  async function handleOptionClick(option: string) {
    if (submitting || feedback?.correct) return

    const isFirstChoice = displayedOptionStates.every((entry) => entry.status === 'idle')
    setSubmitting(true)
    const result = await handleAnswer(option)
    setSubmitting(false)

    if (!result) return
    const acceptedOption = result.selectedOption || option
    setLatestAnswerResult(result)
    if (isFirstChoice) {
      setLocalFirstChoiceStats((current) => ({
        correct: current.correct + (result.correct ? 1 : 0),
        total: current.total + 1,
      }))
    }

    setFeedback({
      correct: result.correct,
      correctOption: result.correctOption,
      dailyTargetJustCompleted: result.dailyTargetJustCompleted,
    })

    if (result.correct) {
      setOptionStates((prev) =>
        prev.map((entry) => (entry.option === acceptedOption ? { ...entry, status: 'correct' } : entry)),
      )

      if (result.dailyTargetJustCompleted) {
        triggerDailyCompletion(result.publicWordbookProgress)
      }

      if (usesSharedAnswerReview) {
        setPendingAdvance(result)
        return
      }
      window.setTimeout(() => feedbackRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 80)
      window.setTimeout(() => {
        void advanceQuiz(result)
      }, showMeaningOptions ? 900 : result.dailyTargetJustCompleted ? 1100 : 500)
      return
    }

    setOptionStates((prev) =>
      prev.map((entry) => (entry.option === acceptedOption ? { ...entry, status: 'wrong' } : entry)),
    )
    window.setTimeout(() => feedbackRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 80)
  }

  function renderExampleLookupPopover() {
    if (!exampleLookupPopover) {
      return null
    }

    const entry = activeExampleLookupEntry
    const detail = entry?.status === 'success' ? entry.detail : null
    const meaningLines = detail ? toPartOfSpeechMeaningLines(detail.meaningCn) : []

    return (
      <div
        ref={exampleLookupPopoverRef}
        className={`quiz-example-lookup-popover${
          exampleLookupPopover.position.centered ? ' quiz-example-lookup-popover--centered' : ''
        }`}
        style={{
          top: `${exampleLookupPopover.position.top}px`,
          left: `${exampleLookupPopover.position.left}px`,
        }}
        role="dialog"
        aria-label={`${exampleLookupPopover.displayWord} \u5355\u8bcd\u91ca\u4e49`}
      >
        <div className="quiz-example-lookup-head">
          <strong>{detail?.word || exampleLookupPopover.displayWord}</strong>
          {detail ? (
            <button
              type="button"
              className="quiz-example-lookup-audio"
              onClick={() => playPromptAudio(detail.audioUrl, detail.word)}
            >
              {'\u8bed\u97f3'}
            </button>
          ) : null}
        </div>
        {detail ? <span className="quiz-example-lookup-phonetic">/{detail.phonetic || '-'}/</span> : null}
        {entry?.status === 'loading' ? <p className="quiz-example-lookup-state">加载中...</p> : null}
        {entry?.status === 'empty' ? <p className="quiz-example-lookup-state">未收录</p> : null}
        {entry?.status === 'error' ? (
          <p className="quiz-example-lookup-state">{entry.message || '\u67e5\u8bcd\u5931\u8d25'}</p>
        ) : null}
        {detail ? (
          meaningLines.length > 0 ? (
            <div className="quiz-example-lookup-meaning-lines">
              {meaningLines.map((line, index) => (
                <p
                  key={`${line.partOfSpeech || 'meaning'}-${index}`}
                  className={`quiz-example-lookup-meaning${
                    line.partOfSpeech ? '' : ' quiz-example-lookup-meaning--plain'
                  }`}
                >
                  {line.partOfSpeech ? <b>{line.partOfSpeech}</b> : null}
                  <span className="multiline-text">{line.meaning}</span>
                </p>
              ))}
            </div>
          ) : (
            <p className="quiz-example-lookup-meaning multiline-text">暂无释义</p>
          )
        ) : null}
      </div>
    )
  }

  function renderWordNotebookModal() {
    if (!wordNotebookModalOpen || !question) {
      return null
    }

    return (
      <div className="modal-backdrop" role="presentation" onClick={closeWordNotebookModal}>
        <section
          className="modal-card word-notebook-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="word-notebook-modal-title"
          onClick={(event) => event.stopPropagation()}
        >
          <div className="word-notebook-modal-head">
            <div>
              <p className="eyebrow">加入单词本</p>
              <h3 id="word-notebook-modal-title">{question.currentWord || question.promptText}</h3>
            </div>
            <button type="button" className="ghost" disabled={wordNotebookSubmitting} onClick={closeWordNotebookModal}>
              关闭
            </button>
          </div>

          <div className="word-notebook-modal-create">
            <input
              value={wordNotebookDraftName}
              onChange={(event) => setWordNotebookDraftName(event.target.value)}
              placeholder="新建单词本名称"
              maxLength={120}
            />
            <button
              type="button"
              className="primary"
              disabled={wordNotebookSubmitting || !wordNotebookDraftName.trim()}
              onClick={() => void createNotebookAndSaveWord()}
            >
              {wordNotebookSubmitting ? '保存中...' : '新建并加入'}
            </button>
          </div>

          <div className="word-notebook-modal-list" role="list" aria-label="可选单词本">
            {currentWordNotebookOptions.map((notebook) => (
              <button
                key={notebook.id}
                type="button"
                role="listitem"
                className={`word-notebook-modal-item${notebook.containsWord ? ' is-collected' : ''}`}
                disabled={wordNotebookSubmitting || notebook.containsWord}
                onClick={() => void saveWordToNotebook(notebook.id)}
              >
                <span>
                  <strong>{notebook.name}</strong>
                  <small>{notebook.wordCount} 个单词</small>
                </span>
                <b>{notebook.containsWord ? '已收录' : '加入'}</b>
              </button>
            ))}
          </div>

          {!loadingCurrentWordNotebookOptions && currentWordNotebookOptions.length === 0 ? (
            <p className="word-notebook-modal-empty">还没有单词本，先在上方创建一个。</p>
          ) : null}
          {loadingCurrentWordNotebookOptions ? <p className="word-notebook-modal-empty">单词本加载中...</p> : null}
        </section>
      </div>
    )
  }

  function renderDailyCompletionCard(className = 'quiz-daily-complete') {
    if (!dailyCompletion) {
      return null
    }

    return (
      <div className={`card ${className}`}>
        <div className="daily-complete-fireworks" aria-hidden="true">
          <span className="daily-complete-burst daily-complete-burst-a" />
          <span className="daily-complete-burst daily-complete-burst-b" />
          <span className="daily-complete-burst daily-complete-burst-c" />
          <span className="daily-complete-burst daily-complete-burst-d" />
          <span className="daily-complete-burst daily-complete-burst-e" />
        </div>
        <div className="quiz-daily-complete-copy">
          <strong>今日学习任务已完成</strong>
          <span>
            今日已背 {dailyCompletion.todayCompletedCount}/{dailyCompletion.dailyTargetCount}，总进度{' '}
            {dailyCompletion.completedCount}/{dailyCompletion.wordCount}。
          </span>
        </div>
      </div>
    )
  }

  const showDailyCompleteFeedback = feedback?.correct && feedback.dailyTargetJustCompleted && dailyCompletion
  const effectiveSession = latestAnswerResult?.session ?? quizState?.session
  const isNotebookQuiz = effectiveSession?.targetType === 'WORD_NOTEBOOK'
  const isWordbookQuiz = Boolean(effectiveSession && effectiveSession.targetType !== 'WORD_NOTEBOOK')
  const activePublicWordbookId =
    effectiveSession?.targetType === 'PUBLIC_WORDBOOK' ? effectiveSession.targetId : selectedPublicWordbook?.id
  const activePublicWordbook =
    publicWordbooks.find((book) => book.id === activePublicWordbookId) ?? selectedPublicWordbook
  const activeWordNotebookId =
    effectiveSession?.targetType === 'WORD_NOTEBOOK' ? effectiveSession.targetId : selectedWordNotebook?.id
  const activeWordNotebook = wordNotebooks.find((notebook) => notebook.id === activeWordNotebookId) ?? selectedWordNotebook
  const showMeaningOptions = Boolean((isPublicQuiz || isNotebookQuiz) && question?.promptType === 'EN_TO_CN')
  const supportsPromptAudio = Boolean((isPublicQuiz || isNotebookQuiz) && question)
  const subscribedPublicWordbooks = publicWordbooks.filter((book) => book.subscribed)
  const dailyTargetModalBook =
    dailyTargetModalBookId == null
      ? null
      : publicWordbooks.find((book) => book.id === dailyTargetModalBookId && book.subscribed) ?? null
  const answeredCount = quizState?.session.answeredQuestions ?? 0
  const totalQuestions = question?.totalQuestions ?? effectiveSession?.totalQuestions ?? 0
  const displayedAnsweredCount =
    question && feedback?.correct
      ? Math.min(totalQuestions, latestAnswerResult?.session.answeredQuestions ?? answeredCount + 1)
      : answeredCount
  const progressPercent = totalQuestions > 0 ? Math.round((displayedAnsweredCount / totalQuestions) * 100) : 0
  const remainingQuestions = Math.max(0, totalQuestions - displayedAnsweredCount)
  const dailyAgendaTarget = (rawAgenda?.newCards ?? 0) + (rawAgenda?.reviewCards ?? 0)
  const latestPublicWordbookProgress = latestAnswerResult?.publicWordbookProgress
  const isPublicQuotaMode = effectiveSession?.targetType === 'PUBLIC_WORDBOOK'
  const dailyTarget =
    latestPublicWordbookProgress?.dailyTargetCount ?? activePublicWordbook?.dailyTargetCount ?? (dailyAgendaTarget || totalQuestions)
  const dailyDone = latestPublicWordbookProgress?.todayCompletedCount ?? activePublicWordbook?.todayCompletedCount ?? displayedAnsweredCount
  const dailyPercent = dailyTarget > 0 ? Math.min(100, Math.round((dailyDone / dailyTarget) * 100)) : 0
  const publicReviewCards = isPublicQuotaMode ? Math.min(dailyDone, dailyTarget) : 0
  const publicNewCards = isPublicQuotaMode ? Math.max(0, dailyTarget - publicReviewCards) : 0
  const publicEstimatedMinutes = isPublicQuotaMode ? Math.ceil(((publicNewCards + publicReviewCards) * 50) / 60) : 0
  const agenda = isPublicQuotaMode
    ? {
        newCards: publicNewCards,
        reviewCards: publicReviewCards,
        estimatedMinutes: publicEstimatedMinutes,
      }
    : rawAgenda
  const goalNewCards = isPublicQuotaMode ? publicNewCards : (agenda?.newCards ?? 0)
  const goalReviewCards = isPublicQuotaMode ? publicReviewCards : (agenda?.reviewCards ?? 0)
  const currentTargetName =
    effectiveSession?.targetType === 'PUBLIC_WORDBOOK'
      ? (activePublicWordbook?.name ?? '\u516c\u5171\u8bcd\u4e66')
      : effectiveSession?.targetType === 'WORD_NOTEBOOK'
        ? (activeWordNotebook?.name ?? '\u5355\u8bcd\u672c')
      : (selectedWordbook?.name ?? '\u6211\u7684\u8bcd\u4e66')
  const currentTargetTotal =
    effectiveSession?.targetType === 'PUBLIC_WORDBOOK'
      ? (activePublicWordbook?.wordCount ?? 0)
      : effectiveSession?.targetType === 'WORD_NOTEBOOK'
        ? (activeWordNotebook?.wordCount ?? 0)
      : (selectedWordbook?.wordCount ?? progress?.totalWords ?? 0)
  const currentTargetCleared =
    effectiveSession?.targetType === 'PUBLIC_WORDBOOK'
      ? (latestPublicWordbookProgress?.completedCount ?? activePublicWordbook?.completedCount ?? 0)
      : effectiveSession?.targetType === 'WORD_NOTEBOOK'
        ? displayedAnsweredCount
      : (selectedWordbook?.clearedCount ?? progress?.clearedWords ?? 0)
  const activeSessionMatchesCurrentPublicBook =
    effectiveSession?.targetType === 'PUBLIC_WORDBOOK' && effectiveSession.targetId === activePublicWordbook?.id
  const rawTodayCorrectAttempts = activeSessionMatchesCurrentPublicBook
    ? effectiveSession?.todayCorrectAttempts
    : activePublicWordbook?.todayCorrectAttempts
  const rawTodayTotalAttempts = activeSessionMatchesCurrentPublicBook
    ? effectiveSession?.todayTotalAttempts
    : activePublicWordbook?.todayTotalAttempts
  const fallbackTodayCorrectAttempts = firstChoiceBaseline.correct + localFirstChoiceStats.correct
  const fallbackTodayTotalAttempts = firstChoiceBaseline.total + localFirstChoiceStats.total
  const hasBackendTodayStats = typeof rawTodayCorrectAttempts === 'number' && typeof rawTodayTotalAttempts === 'number'
  const todayTotalAttempts =
    hasBackendTodayStats ? rawTodayTotalAttempts : fallbackTodayTotalAttempts
  const todayCorrectAttempts =
    hasBackendTodayStats ? rawTodayCorrectAttempts : fallbackTodayCorrectAttempts
  const todayQuotaAccuracy = todayTotalAttempts > 0 ? Math.round((todayCorrectAttempts / todayTotalAttempts) * 100) : 0
  const optionLabels = ['A', 'B', 'C', 'D']
  const displayedOptionStates =
    !feedback && question
      ? question.options.map((option) => ({
          option,
          status: 'idle' as const,
        }))
      : optionStates
  const usesSharedAnswerReview = Boolean((isPublicQuiz || isNotebookQuiz) && showMeaningOptions)
  const exampleSentence = formatMultilineText(question?.correctedExampleSentence || question?.exampleSentence)
  const chineseSentence = formatMultilineText(question?.chineseSentence)
  const showExampleCard = Boolean(usesSharedAnswerReview && feedback?.correct && pendingAdvance)
  const showExampleAudioButton = Boolean(showExampleCard && exampleSentence)
  const showFeedbackBanner = Boolean(showDailyCompleteFeedback || (feedback && !feedback.correct))
  const exampleSentenceTokens = tokenizeExampleSentence(exampleSentence)
  const currentQuestionWordCachePrefix = question?.attemptId != null ? `${question.attemptId}:` : ''
  const currentWordCollected = currentWordNotebookOptions.some((notebook) => notebook.containsWord)
  const activeExampleLookupEntry = exampleLookupPopover ? exampleLookupCache[exampleLookupPopover.key] : null

  const handleExampleWordClick = useCallback(
    (token: ExampleSentenceToken, event: ReactMouseEvent<HTMLButtonElement>) => {
      const normalizedWord = token.normalized ?? ''
      const cacheKey = `${currentQuestionWordCachePrefix}${normalizedWord}`
      if (!normalizedWord) {
        return
      }

      if (exampleLookupPopover?.key === cacheKey) {
        setExampleLookupPopover(null)
        activeExampleLookupAnchorRef.current = null
        return
      }

      activeExampleLookupAnchorRef.current = event.currentTarget
      setExampleLookupPopover({
        key: cacheKey,
        word: normalizedWord,
        displayWord: token.value,
        position: updateExampleLookupPosition(event.currentTarget),
      })

      if (!exampleLookupCache[cacheKey]) {
        void loadExampleLookupDetail(normalizedWord, cacheKey)
      }
    },
    [currentQuestionWordCachePrefix, exampleLookupCache, exampleLookupPopover?.key, loadExampleLookupDetail, updateExampleLookupPosition],
  )

  const openWordNotebookModal = useCallback(async () => {
    if (!token) {
      openAuthModal()
      return
    }
    if (!notebookLookupWord) {
      return
    }
    setWordNotebookModalOpen(true)
    setWordNotebookDraftName('')
    await loadCurrentWordNotebookOptions(notebookLookupWord)
  }, [loadCurrentWordNotebookOptions, notebookLookupWord, openAuthModal, token])

  const toggleCurrentWordNotebookState = useCallback(async () => {
    if (!notebookLookupWord || wordNotebookSubmitting) {
      return
    }

    if (currentWordCollected) {
      setWordNotebookSubmitting(true)
      const removed = await handleRemoveWordNotebookEntry(notebookLookupWord)
      if (removed != null) {
        const updatedNotebooks = await loadCurrentWordNotebookOptions(notebookLookupWord)
        setCurrentWordNotebookOptions(updatedNotebooks)
        setWordNotebookModalOpen(false)
        setWordNotebookDraftName('')
      }
      setWordNotebookSubmitting(false)
      return
    }

    await openWordNotebookModal()
  }, [
    currentWordCollected,
    handleRemoveWordNotebookEntry,
    loadCurrentWordNotebookOptions,
    notebookLookupWord,
    openWordNotebookModal,
    wordNotebookSubmitting,
  ])

  const closeWordNotebookModal = useCallback(() => {
    if (wordNotebookSubmitting) {
      return
    }
    setWordNotebookModalOpen(false)
    setWordNotebookDraftName('')
  }, [wordNotebookSubmitting])

  const saveWordToNotebook = useCallback(
    async (notebookId: number) => {
      if (!notebookLookupWord || wordNotebookSubmitting) {
        return
      }

      setWordNotebookSubmitting(true)
      const entry = await resolveCurrentWordNotebookEntry()
      if (entry) {
        const result = await handleAddWordNotebookEntry(notebookId, entry)
        if (result) {
          const updatedNotebooks = await loadCurrentWordNotebookOptions(notebookLookupWord)
          setCurrentWordNotebookOptions(updatedNotebooks)
          setWordNotebookModalOpen(false)
          setWordNotebookDraftName('')
        }
      }
      setWordNotebookSubmitting(false)
    },
    [handleAddWordNotebookEntry, loadCurrentWordNotebookOptions, notebookLookupWord, resolveCurrentWordNotebookEntry, wordNotebookSubmitting],
  )

  const createNotebookAndSaveWord = useCallback(async () => {
    const notebookName = wordNotebookDraftName.trim()
    if (!notebookName || wordNotebookSubmitting) {
      return
    }

    setWordNotebookSubmitting(true)
    const created = await handleCreateWordNotebook(notebookName)
    if (created) {
      if (notebookLookupWord) {
        const createdOptions = await loadCurrentWordNotebookOptions(notebookLookupWord)
        setCurrentWordNotebookOptions(createdOptions)
      }
      const entry = await resolveCurrentWordNotebookEntry()
      if (entry) {
        const result = await handleAddWordNotebookEntry(created.id, entry)
        if (result && notebookLookupWord) {
          const updatedNotebooks = await loadCurrentWordNotebookOptions(notebookLookupWord)
          setCurrentWordNotebookOptions(updatedNotebooks)
          setWordNotebookModalOpen(false)
          setWordNotebookDraftName('')
        }
      }
    }
    setWordNotebookSubmitting(false)
  }, [
    handleAddWordNotebookEntry,
    handleCreateWordNotebook,
    loadCurrentWordNotebookOptions,
    notebookLookupWord,
    resolveCurrentWordNotebookEntry,
    wordNotebookDraftName,
    wordNotebookSubmitting,
  ])

  useEffect(() => {
    if (!showExampleCard) {
      setExampleLookupPopover(null)
      activeExampleLookupAnchorRef.current = null
    }
  }, [showExampleCard])

  useEffect(() => {
    if (!exampleLookupPopover || !activeExampleLookupAnchorRef.current) {
      return
    }

    const updatePosition = () => {
      const anchor = activeExampleLookupAnchorRef.current
      if (!anchor) {
        return
      }
      setExampleLookupPopover((current) =>
        current
          ? {
              ...current,
              position: updateExampleLookupPosition(anchor),
            }
          : current,
      )
    }

    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node
      if (exampleLookupPopoverRef.current?.contains(target) || activeExampleLookupAnchorRef.current?.contains(target)) {
        return
      }
      setExampleLookupPopover(null)
      activeExampleLookupAnchorRef.current = null
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setExampleLookupPopover(null)
        activeExampleLookupAnchorRef.current = null
      }
    }

    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [exampleLookupPopover, updateExampleLookupPosition])

  useEffect(() => {
    if (!showExampleCard || !question) {
      return
    }

    if (autoPlayedExampleQuestionIdRef.current === question.attemptId) {
      return
    }
    autoPlayedExampleQuestionIdRef.current = question.attemptId

    const timer = window.setTimeout(() => {
      scrollAnsweredContentIntoView()
      replayExampleSentence(question.exampleAudioUrl || '', exampleSentence)
    }, 140)

    return () => window.clearTimeout(timer)
  }, [
    exampleSentence,
    question?.attemptId,
    question?.exampleAudioUrl,
    replayExampleSentence,
    scrollAnsweredContentIntoView,
    showExampleCard,
  ])

  function resolvePublicBookDisplayStats(book: typeof publicWordbooks[number]) {
    const isActiveBook = isPublicQuiz && effectiveSession?.targetId === book.id
    const correct = (isActiveBook ? todayCorrectAttempts : book.todayCorrectAttempts) ?? 0
    const total = (isActiveBook ? todayTotalAttempts : book.todayTotalAttempts) ?? 0
    const accuracy = total > 0 ? Math.round((correct / total) * 100) : 0
    const bookDailyDone =
      isActiveBook && latestPublicWordbookProgress ? latestPublicWordbookProgress.todayCompletedCount : book.todayCompletedCount
    const bookCompleted =
      isActiveBook && latestPublicWordbookProgress ? latestPublicWordbookProgress.completedCount : book.completedCount
    const bookDailyTarget =
      isActiveBook && latestPublicWordbookProgress ? latestPublicWordbookProgress.dailyTargetCount : book.dailyTargetCount
    const bookWordCount = isActiveBook && latestPublicWordbookProgress ? latestPublicWordbookProgress.wordCount : book.wordCount
    const progressRate = bookWordCount > 0 ? Math.min(100, Math.round((bookCompleted / bookWordCount) * 100)) : 0
    return { accuracy, correct, total, bookDailyDone, bookDailyTarget, bookCompleted, bookWordCount, progressRate }
  }

  return (
    <div className="quiz-console">
      <aside className="quiz-rail quiz-rail--left" aria-label={'\u5b66\u4e60\u8bbe\u7f6e'}>
        <section className="quiz-panel quiz-goal-panel">
          <div className="quiz-panel-head">
            <h2>{'\u4eca\u65e5\u76ee\u6807'}</h2>
            <button type="button" onClick={openDailyTargetModal} disabled={subscribedPublicWordbooks.length === 0}>
              编辑目标
            </button>
          </div>
          <div className="quiz-goal-body">
            <div className="quiz-goal-ring" style={{ '--goal-rate': `${dailyPercent}` } as CSSProperties}>
              <span>{dailyPercent}%</span>
              <small>已完成</small>
            </div>
            <div className="quiz-goal-metrics">
              <span>{'\u5b66\u4e60\u65b0\u8bcd'}</span>
              <strong>{goalNewCards}</strong>
              <span>{'\u590d\u4e60\u5355\u8bcd'}</span>
              <strong>{goalReviewCards}</strong>
              <small>{`\u9884\u8ba1\u8fd8\u9700 ${agenda?.estimatedMinutes ?? 0} \u5206\u949f\u5b8c\u6210`}</small>
            </div>
          </div>
        </section>

        <section className="quiz-panel quiz-mode-panel">
          <div className="quiz-panel-head">
            <h2>{'\u7b54\u9898\u9009\u9879'}</h2>
          </div>
          <div
            className={`quiz-option-strategy${optionStrategyOpen ? ' open' : ''}`}
            onBlur={(event) => {
              const nextFocus = event.relatedTarget as Node | null
              if (!nextFocus || !event.currentTarget.contains(nextFocus)) {
                setOptionStrategyOpen(false)
              }
            }}
          >
            <button
              type="button"
              className="quiz-option-strategy-trigger"
              aria-haspopup="listbox"
              aria-expanded={optionStrategyOpen}
              onClick={() => setOptionStrategyOpen((open) => !open)}
            >
              <span>{selectedOptionStrategy.label}</span>
              <small>{selectedOptionStrategy.tooltip}</small>
              <i aria-hidden="true" />
            </button>
            <div className="quiz-option-strategy-menu" role="listbox" aria-label={'\u7b54\u9898\u9009\u9879'}>
              {QUIZ_OPTION_STRATEGIES.map((strategy) => (
                <button
                  key={strategy.value}
                  type="button"
                  role="option"
                  aria-selected={strategy.value === effectiveQuizOptionStrategy}
                  className={strategy.value === effectiveQuizOptionStrategy ? 'active' : ''}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => selectOptionStrategy(strategy.value)}
                >
                  <span>{strategy.label}</span>
                  <b role="tooltip">{strategy.tooltip}</b>
                </button>
              ))}
            </div>
          </div>
        </section>

        <section className="quiz-panel quiz-book-panel">
          <div className="quiz-panel-head">
            <h2>{'\u8bcd\u4e66\u4fe1\u606f'}</h2>
            <span className="quiz-status-pill">{isWordbookQuiz ? '正在学习' : '待开始'}</span>
          </div>
          <div className="quiz-book-list">
            {subscribedPublicWordbooks.length > 0 ? (
              subscribedPublicWordbooks.map((book) => {
                const stats = resolvePublicBookDisplayStats(book)
                const active = isPublicQuiz && effectiveSession?.targetId === book.id
                return (
                  <button
                    key={book.id}
                    type="button"
                    className={`quiz-book-option${active ? ' active' : ''}`}
                    onClick={() => startPublicWordbookQuiz(book.id)}
                    disabled={creatingQuiz}
                  >
                    <QuizBookArtwork name={book.name} kind="public" />
                    <span className="quiz-book-option-main">
                      <strong className="quiz-book-option-title">
                        {book.name}
                        {isPublicQuiz && effectiveSession?.targetId === book.id ? (
                          <em className="word-notebook-status-pill">正在学习</em>
                        ) : null}
                      </strong>
                      <small>
                        今日 {stats.bookDailyDone}/{stats.bookDailyTarget || '-'} · 正确率 {stats.accuracy}%
                      </small>
                      <i className="quiz-mini-track" aria-label={`${book.name} 词书进度 ${stats.progressRate}%`}>
                        <span style={{ width: `${stats.progressRate}%` }} />
                      </i>
                      <small>
                        总进度 {stats.bookCompleted}/{stats.bookWordCount || '-'}
                      </small>
                    </span>
                  </button>
                )
              })
            ) : (
              <span className="quiz-muted">还没有订阅公共词书。</span>
            )}
          </div>
          {quizState?.session.targetType === 'USER_WORDBOOK' ? (
            <div className="quiz-book-line">
              <QuizBookArtwork name={currentTargetName} kind="imported" />
              <div>
                <strong className="quiz-book-option-title">
                  {currentTargetName}
                  <em className="word-notebook-status-pill">正在学习</em>
                </strong>
                <span>
                  {`\u5df2\u5b66 ${currentTargetCleared} / ${currentTargetTotal || '-'}`}
                </span>
              </div>
            </div>
          ) : null}
        </section>
      </aside>

      <main className="quiz-main" aria-label={'\u5355\u8bcd\u7ec3\u4e60'}>
        {showDailyCompleteFeedback ? renderDailyCompletionCard('quiz-daily-complete quiz-daily-complete--inline') : null}

        <section className="quiz-study-card">
          <div className="quiz-study-head">
            <div>
              <span className="quiz-section-mark">单词练习</span>
              <h2>{question ? '选择正确答案' : '还没有开始背词'}</h2>
            </div>
            <div className="quiz-progress-compact">
              <span className="quiz-progress-line">
                <i style={{ width: `${progressPercent}%` }} />
              </span>
              <strong>
                {displayedAnsweredCount} / {totalQuestions || '-'}
              </strong>
            </div>
          </div>

          {question ? (
            <>
              <div className="quiz-word-block">
                <div className="quiz-word-row">
                  {supportsPromptAudio ? (
                    <button type="button" className="quiz-word-audio-button" onClick={replayCurrentWord}>
                      <strong>{question.promptText}</strong>
                    </button>
                  ) : (
                    <strong>{question.promptText}</strong>
                  )}
                  {isPublicQuiz ? (
                    <button
                      type="button"
                      className={`quiz-word-star${currentWordCollected ? ' is-collected' : ''}`}
                      aria-label={currentWordCollected ? `取消收藏 ${question.currentWord}` : `将 ${question.currentWord} 加入单词本`}
                      title={currentWordCollected ? '取消收藏' : '加入单词本'}
                      onClick={() => void toggleCurrentWordNotebookState()}
                    >
                      {currentWordCollected ? '★' : '☆'}
                    </button>
                  ) : null}
                </div>
                {supportsPromptAudio ? (
                  <button
                    type="button"
                    className="quiz-word-audio-button quiz-word-phonetic phonetic-text"
                    onClick={replayCurrentWord}
                  >
                    /{question.phonetic || '-'}/
                  </button>
                ) : isNotebookQuiz ? (
                  <span className="quiz-word-phonetic phonetic-text">/{question.phonetic || '-'}/</span>
                ) : null}
                <p>根据英文选择最贴近的中文释义</p>
              </div>

              <div className="options quiz-answer-grid">
                {displayedOptionStates.map(({ option, status }, index) => (
                  <button
                    key={option}
                    type="button"
                    className={`option option--${status}`}
                    onClick={() => void handleOptionClick(option)}
                    disabled={submitting || status === 'wrong' || feedback?.correct === true}
                    title={showMeaningOptions ? option : undefined}
                  >
                    <span className="option-letter">{optionLabels[index] ?? index + 1}</span>
                    {renderOptionText(option, status)}
                    <span className="option-icon">{status === 'correct' ? '✓' : status === 'wrong' ? '✕' : ''}</span>
                  </button>
                ))}
              </div>

              {showMeaningOptions && feedback?.correct ? (
                <div ref={fullMeaningRef} className="quiz-full-meaning">
                  <span>{'\u5b8c\u6574\u91ca\u4e49'}</span>
                  <div className="quiz-full-meaning-lines">
                    {toPartOfSpeechMeaningLines(feedback.correctOption).map((line, index) => (
                      <p key={`${line.partOfSpeech || 'meaning'}-${index}`}>
                        {line.partOfSpeech ? <b>{line.partOfSpeech}</b> : null}
                        <strong>{line.meaning}</strong>
                      </p>
                    ))}
                  </div>
                </div>
              ) : null}

              <div className="quiz-study-actions">
                {pendingAdvance ? (
                  <button type="button" className="primary" onClick={handleNextQuestion}>
                    下一个
                  </button>
                ) : null}
                <span>{remainingQuestions > 0 ? `剩余 ${remainingQuestions} 题` : '本轮已完成'}</span>
              </div>

              {showExampleCard ? (
                <div ref={answerDetailRef} className="quiz-example-card">
                  <div className="quiz-example-card-head">
                    <span>{'\u4f8b\u53e5\u4fe1\u606f'}</span>
                    {showExampleAudioButton ? (
                      <button
                        type="button"
                        className="quiz-example-audio-button"
                        onClick={() => replayExampleSentence(question?.exampleAudioUrl || '', exampleSentence)}
                      >
                        {'\u64ad\u653e\u4f8b\u53e5'}
                      </button>
                    ) : null}
                  </div>
                  {exampleSentence ? (
                    <div className="quiz-example-text quiz-example-text--interactive multiline-text">
                      {exampleSentenceTokens.map((token, index) =>
                        token.type === 'word' && token.normalized ? (
                          <button
                            key={`${token.value}-${index}`}
                            type="button"
                            className={`quiz-example-word${
                              exampleLookupPopover?.key === `${currentQuestionWordCachePrefix}${token.normalized}`
                                ? ' quiz-example-word--active'
                                : ''
                            }`}
                            onClick={(event) => handleExampleWordClick(token, event)}
                          >
                            {token.value}
                          </button>
                        ) : (
                          <span key={`${token.value}-${index}`} className="quiz-example-separator">
                            {token.value}
                          </span>
                        ),
                      )}
                    </div>
                  ) : null}
                  {chineseSentence ? (
                    <p className="quiz-example-translation multiline-text">{chineseSentence}</p>
                  ) : null}
                  {!exampleSentence && !chineseSentence ? (
                    <p className="quiz-example-empty">暂无例句信息</p>
                  ) : null}
                </div>
              ) : null}

              {showExampleCard ? renderExampleLookupPopover() : null}

              {showFeedbackBanner ? (
                <div
                  ref={feedbackRef}
                  className={`quiz-feedback${
                    feedback?.correct ? ' quiz-feedback--correct' : feedback ? ' quiz-feedback--wrong' : ' quiz-feedback--hidden'
                  }${showDailyCompleteFeedback ? ' quiz-feedback--celebrate' : ''}`}
                >
                  {showDailyCompleteFeedback ? null : feedback ? (
                    <>
                      <span className="quiz-feedback-icon">✕</span>
                      <span>回答错误，请继续选择。</span>
                    </>
                  ) : null}
                </div>
              ) : null}
            </>
          ) : (
            <div className="quiz-empty-state">
              <strong>{quizState ? '本轮内容已经学完' : '选择词书或单词本开始练习'}</strong>
              <span>从词书页或单词本页选定学习范围，也可以直接用当前目标创建一轮练习。</span>
              <button type="button" className="primary" onClick={() => void onCreateQuiz()} disabled={creatingQuiz}>
                {creatingQuiz ? '创建中...' : '开始背词'}
              </button>
            </div>
          )}
        </section>
      </main>
      <aside className="quiz-rail quiz-rail--right" aria-label="学习概览">
        <section className="quiz-panel quiz-overview-panel">
          <div className="quiz-panel-head">
            <h2>学习概览</h2>
            <Link to="/progress">查看更多</Link>
          </div>
          <div className="quiz-stat-grid">
            <div className="quiz-stat-tile">
              <span>今日配额正确率</span>
              <strong>{todayQuotaAccuracy}%</strong>
              <small>
                {todayCorrectAttempts} / {todayTotalAttempts}
              </small>
            </div>
            <div className="quiz-stat-tile">
              <span>学习词书</span>
              <strong>{progress?.wordbooks ?? 0}</strong>
            </div>
          </div>
          <div className="quiz-overview-actions">
            <div className="quiz-panel-head quiz-panel-head--subsection">
              <h2>快速操作</h2>
            </div>
            <div className="quiz-action-grid">
              <Link to="/search">查单词</Link>
              <Link to="/notebooks">单词本</Link>
              <Link to="/progress">学习记录</Link>
              <button type="button" onClick={handleResetProgress} disabled={!isPublicQuiz || creatingQuiz}>
                重置进度
              </button>
            </div>
          </div>
        </section>
        <section className="quiz-panel quiz-book-panel quiz-notebook-panel">
          <div className="quiz-panel-head">
            <h2>单词本信息</h2>
            <span className="quiz-status-pill">{isNotebookQuiz ? '正在学习' : '待开始'}</span>
          </div>
          <div className="quiz-book-list">
            {wordNotebooks.length > 0 ? (
              wordNotebooks.map((notebook) => (
                <button
                  key={notebook.id}
                  type="button"
                  className={`quiz-book-option${isNotebookQuiz && activeWordNotebookId === notebook.id ? ' active' : ''}`}
                  onClick={() => startWordNotebookQuiz(notebook.id)}
                  disabled={creatingQuiz}
                >
                  <QuizNotebookCover name={notebook.name} />
                  <span className="quiz-book-option-main">
                    <strong className="quiz-book-option-title">
                      {notebook.name}
                      {isNotebookQuiz && activeWordNotebookId === notebook.id ? (
                        <em className="word-notebook-status-pill">正在学习</em>
                      ) : null}
                    </strong>
                    <small>收藏 {notebook.wordCount} 个单词</small>
                    <i
                      className="quiz-mini-track"
                      aria-label={`${notebook.name} 单词本容量 ${isNotebookQuiz && currentTargetTotal > 0 && activeWordNotebookId === notebook.id ? progressPercent : 0}%`}
                    >
                      <span
                        style={{
                          width: `${
                            isNotebookQuiz && activeWordNotebookId === notebook.id && currentTargetTotal > 0
                              ? Math.min(100, Math.round((currentTargetCleared / currentTargetTotal) * 100))
                              : 0
                          }%`,
                        }}
                      />
                    </i>
                    <small>
                      {isNotebookQuiz && activeWordNotebookId === notebook.id
                        ? `本轮进度 ${currentTargetCleared}/${currentTargetTotal || '-'}`
                        : `已收录 ${notebook.wordCount} 个单词`}
                    </small>
                  </span>
                </button>
              ))
            ) : (
              <span className="quiz-muted">还没有创建单词本。</span>
            )}
          </div>
        </section>
      </aside>

      {dailyTargetModalBook && (
        <DailyTargetModal
          book={dailyTargetModalBook}
          savingDailyTarget={savingDailyTarget}
          continueAfterTargetUpdate={isDailyQuotaCompleted(dailyTargetModalBook)}
          onClose={closeDailyTargetModal}
          onSelectTarget={(target) => void updateDailyTarget(target)}
        />
      )}
      <QuizAiAssistant token={token || ''} question={question ?? null} onUnauthorized={clearAuth} />
      {renderWordNotebookModal()}
    </div>
  )
}

function QuizNotebookCover({ name }: { name: string }) {
  return (
    <div className="quiz-book-cover quiz-book-cover--notebook is-fallback" aria-hidden="true">
      <span>{name.charAt(0).toUpperCase()}</span>
    </div>
  )
}
