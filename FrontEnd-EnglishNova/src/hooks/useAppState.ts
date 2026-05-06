import { useDeferredValue, useEffect, useEffectEvent, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { ApiAuthOptions } from '../api/client'
import { authApi, type AuthUser } from '../api/modules/auth'
import { importApi, type ImportPlatform, type ImportPreset } from '../api/modules/imports'
import {
  quizApi,
  type QuizAnswerResult,
  type QuizMode,
  type QuizOptionStrategy,
  type QuizSessionState,
  type QuizTargetType,
  type VocabularyEntry,
  type WordbookProgress,
  type WordbookSummary,
} from '../api/modules/quiz'
import {
  searchApi,
  type PublicWordbook,
  type SearchEntryType,
  type SearchSuggestion,
  type WordSearchResponse,
} from '../api/modules/search'
import { studyApi, type StudyAgenda, type StudyProgress } from '../api/modules/study'
import { systemApi, type SystemOverview } from '../api/modules/system'
import {
  wordNotebookApi,
  type AddWordNotebookEntryRequest,
  type WordNotebookEntry,
  type WordNotebookSummary,
} from '../api/modules/wordNotebooks'
import { AUTH_IDLE_TIMEOUT_MS, DEFAULT_IMPORT_PLATFORM, TOKEN_KEY } from '../constants'

export type GlobalLayoutMode = 'pixel' | 'default'

interface StoredAuthSession {
  token: string
  lastActivityAt: number
}

interface PendingGlobalNotice {
  type: 'error' | 'message'
  text: string
}

interface FlashNotice {
  type: 'error' | 'success'
  text: string
}

const LAYOUT_MODE_KEY = 'english-nova.layout-mode'
const QUIZ_SESSION_KEY = 'english-nova.quiz-session-id'
const PENDING_NOTICE_KEY = 'english-nova.pending-notice'
const AUTO_AUTH_MODAL_SUPPRESS_MS = 1200
const SESSION_ACTIVITY_THROTTLE_MS = 1000
const SESSION_ACTIVITY_EVENTS: Array<keyof WindowEventMap> = ['pointerdown', 'keydown', 'input', 'touchstart', 'scroll']

function readLayoutMode(): GlobalLayoutMode {
  return localStorage.getItem(LAYOUT_MODE_KEY) === 'default' ? 'default' : 'pixel'
}

function readStoredSession(): StoredAuthSession | null {
  localStorage.removeItem(TOKEN_KEY)

  const raw = sessionStorage.getItem(TOKEN_KEY)
  if (!raw) {
    return null
  }

  try {
    const session = JSON.parse(raw) as StoredAuthSession
    const inactiveMs = Date.now() - session.lastActivityAt
    if (!session.token || typeof session.lastActivityAt !== 'number' || inactiveMs >= AUTH_IDLE_TIMEOUT_MS) {
      sessionStorage.removeItem(TOKEN_KEY)
      return null
    }
    return session
  } catch {
    sessionStorage.removeItem(TOKEN_KEY)
    return null
  }
}

function createStoredSession(token: string): StoredAuthSession {
  return {
    token,
    lastActivityAt: Date.now(),
  }
}

function persistSession(session: StoredAuthSession) {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.setItem(TOKEN_KEY, JSON.stringify(session))
}

function clearStoredToken() {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
}

function readStoredQuizSessionId() {
  return sessionStorage.getItem(QUIZ_SESSION_KEY)
}

function persistQuizSessionId(sessionId: string) {
  sessionStorage.setItem(QUIZ_SESSION_KEY, sessionId)
}

function clearStoredQuizSessionId() {
  sessionStorage.removeItem(QUIZ_SESSION_KEY)
}

function writePendingNotice(notice: PendingGlobalNotice) {
  sessionStorage.setItem(PENDING_NOTICE_KEY, JSON.stringify(notice))
}

function readPendingNotice() {
  const raw = sessionStorage.getItem(PENDING_NOTICE_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as PendingGlobalNotice
  } catch {
    sessionStorage.removeItem(PENDING_NOTICE_KEY)
    return null
  }
}

function clearPendingNotice() {
  sessionStorage.removeItem(PENDING_NOTICE_KEY)
}

export function useAppState() {
  const navigate = useNavigate()
  const location = useLocation()
  const [storedSession, setStoredSession] = useState<StoredAuthSession | null>(readStoredSession)
  const [user, setUser] = useState<AuthUser | null>(null)
  const [layoutMode, setLayoutModeState] = useState<GlobalLayoutMode>(readLayoutMode)
  const token = storedSession?.token ?? ''
  const [authModalOpen, setAuthModalOpen] = useState(false)

  const [authTab, setAuthTab] = useState<'login' | 'register'>('login')

  const [overview, setOverview] = useState<SystemOverview | null>(null)
  const [agenda, setAgenda] = useState<StudyAgenda | null>(null)
  const [progress, setProgress] = useState<StudyProgress | null>(null)
  const [presets, setPresets] = useState<ImportPreset[]>([])
  const [wordbooks, setWordbooks] = useState<WordbookSummary[]>([])
  const [entries, setEntries] = useState<VocabularyEntry[]>([])
  const [wordNotebooks, setWordNotebooks] = useState<WordNotebookSummary[]>([])
  const [selectedWordNotebookId, setSelectedWordNotebookId] = useState<number | null>(null)
  const [wordNotebookEntries, setWordNotebookEntries] = useState<WordNotebookEntry[]>([])
  const [wordNotebookEntriesLoading, setWordNotebookEntriesLoading] = useState(false)
  const [publicWordbooks, setPublicWordbooks] = useState<PublicWordbook[]>([])
  const [selectedPublicWordbookId, setSelectedPublicWordbookId] = useState<number | null>(null)
  const [wordbookProgress, setWordbookProgress] = useState<WordbookProgress | null>(null)
  const [selectedWordbookId, setSelectedWordbookId] = useState<number | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const deferredSearchQuery = useDeferredValue(searchQuery)
  const [searchResult, setSearchResult] = useState<WordSearchResponse>({ hits: [] })
  const [searchSuggestions, setSearchSuggestions] = useState<SearchSuggestion[]>([])
  const [librarySearchQuery, setLibrarySearchQuery] = useState('')
  const deferredLibrarySearchQuery = useDeferredValue(librarySearchQuery)
  const [librarySearchResult, setLibrarySearchResult] = useState<WordSearchResponse>({ hits: [] })
  const [librarySearchSuggestions, setLibrarySearchSuggestions] = useState<SearchSuggestion[]>([])
  const [quizMode, setQuizMode] = useState<QuizMode>('MIXED')
  const [quizOptionStrategy, setQuizOptionStrategy] = useState<QuizOptionStrategy>('RANDOM')
  const [quizState, setQuizState] = useState<QuizSessionState | null>(null)
  const [creatingQuiz, setCreatingQuiz] = useState(false)
  const [subscribingPublicWordbookId, setSubscribingPublicWordbookId] = useState<number | null>(null)
  const [unsubscribingPublicWordbookId, setUnsubscribingPublicWordbookId] = useState<number | null>(null)
  const [resettingPublicWordbookId, setResettingPublicWordbookId] = useState<number | null>(null)

  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [flashNotice, setFlashNotice] = useState<FlashNotice | null>(null)
  const [loading, setLoading] = useState(false)

  const [account, setAccount] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [registerUsername, setRegisterUsername] = useState('')
  const [registerEmail, setRegisterEmail] = useState('')
  const [registerPassword, setRegisterPassword] = useState('')
  const [sourceName, setSourceName] = useState('')
  const [selectedPlatform, setSelectedPlatform] = useState<ImportPlatform>(DEFAULT_IMPORT_PLATFORM)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  const storedSessionRef = useRef(storedSession)
  const lastActivityAtRef = useRef(storedSession?.lastActivityAt ?? 0)
  const lastPersistedActivityAtRef = useRef(storedSession?.lastActivityAt ?? 0)
  const idleTimerRef = useRef<number | null>(null)
  const suppressAutoAuthModalUntilRef = useRef(0)
  const hasTrackedRouteActivityRef = useRef(false)
  const loadPrivateDataRequestIdRef = useRef(0)
  const loadPublicDataRequestIdRef = useRef(0)

  useEffect(() => {
    const pendingNotice = readPendingNotice()
    if (!pendingNotice) {
      return
    }

    clearPendingNotice()
    if (pendingNotice.type === 'error') {
      setFlashNotice({ type: 'error', text: pendingNotice.text })
    } else {
      setFlashNotice({ type: 'success', text: pendingNotice.text })
    }
  }, [token, location.pathname, location.search])

  function clearIdleTimer() {
    if (idleTimerRef.current !== null) {
      window.clearTimeout(idleTimerRef.current)
      idleTimerRef.current = null
    }
  }

  function storeSession(session: StoredAuthSession) {
    persistSession(session)
    storedSessionRef.current = session
    lastActivityAtRef.current = session.lastActivityAt
    lastPersistedActivityAtRef.current = session.lastActivityAt
    setStoredSession(session)
  }

  function clearSessionState() {
    clearStoredToken()
    storedSessionRef.current = null
    lastActivityAtRef.current = 0
    lastPersistedActivityAtRef.current = 0
    setStoredSession(null)
  }

  function authOptions(authToken = token): ApiAuthOptions {
    return { token: authToken, onUnauthorized: clearAuth }
  }

  function setLayoutMode(nextMode: GlobalLayoutMode) {
    setLayoutModeState(nextMode)
    localStorage.setItem(LAYOUT_MODE_KEY, nextMode)
  }

  function shouldSuppressAutoAuthModal() {
    return Date.now() < suppressAutoAuthModalUntilRef.current
  }

  function openAuthModal(nextTab: 'login' | 'register' = 'login') {
    setAuthTab(nextTab)
    setAuthModalOpen(true)
    setError(null)
    setMessage(null)
  }

  function openAuthModalIfAllowed(nextTab: 'login' | 'register' = 'login') {
    if (shouldSuppressAutoAuthModal()) {
      return
    }
    openAuthModal(nextTab)
  }

  function closeAuthModal() {
    if (loading) return
    setAuthModalOpen(false)
    setError(null)
  }

  function clearAuthError() {
    setError(null)
  }

  function clearAuth(options?: { notice?: { type: 'error' | 'message'; text: string } }) {
    suppressAutoAuthModalUntilRef.current = Date.now() + AUTO_AUTH_MODAL_SUPPRESS_MS
    clearIdleTimer()
    clearSessionState()
    hasTrackedRouteActivityRef.current = false
    setUser(null)
    setAuthModalOpen(false)
    navigate('/')
    setAgenda(null)
    setProgress(null)
    setPresets([])
    setWordbooks([])
    setEntries([])
    setWordNotebooks([])
    setSelectedWordNotebookId(null)
    setWordNotebookEntries([])
    setPublicWordbooks([])
    setSelectedPublicWordbookId(null)
    setWordbookProgress(null)
    setSelectedWordbookId(null)
    setQuizState(null)
    clearStoredQuizSessionId()
    setCreatingQuiz(false)
    setSubscribingPublicWordbookId(null)
    setUnsubscribingPublicWordbookId(null)
    setResettingPublicWordbookId(null)
    setSearchResult({ hits: [] })
    setSearchSuggestions([])
    setSearchQuery('')
    setLibrarySearchQuery('')
    setLibrarySearchResult({ hits: [] })
    setLibrarySearchSuggestions([])
    void loadPublicData()
    const notice = options?.notice
    if (notice) {
      writePendingNotice(notice)
      window.setTimeout(() => {
        if (notice.type === 'error') {
          setFlashNotice({ type: 'error', text: notice.text })
        } else {
          setFlashNotice({ type: 'success', text: notice.text })
        }
      }, 0)
    }
  }

  function syncPublicWordbookProgress(progress: {
    publicWordbookId: number
    completedCount: number
    dailyTargetCount: number
    todayCompletedCount: number
    wordCount: number
    todayCorrectAttempts?: number
    todayTotalAttempts?: number
  }) {
    setPublicWordbooks((current) =>
      current.map((book) =>
        book.id === progress.publicWordbookId
          ? {
              ...book,
              wordCount: progress.wordCount,
              completedCount: progress.completedCount,
              dailyTargetCount: progress.dailyTargetCount,
              todayCompletedCount: progress.todayCompletedCount,
              todayCorrectAttempts: progress.todayCorrectAttempts ?? book.todayCorrectAttempts,
              todayTotalAttempts: progress.todayTotalAttempts ?? book.todayTotalAttempts,
            }
          : book,
      ),
    )
  }

  const handleIdleLogout = useEffectEvent(() => {
    clearAuth({ notice: { type: 'error', text: '30 分钟无操作，已自动退出登录' } })
  })

  const scheduleIdleLogout = useEffectEvent(() => {
    clearIdleTimer()

    const currentSession = storedSessionRef.current
    if (!currentSession?.token) {
      return
    }

    const remainingMs = AUTH_IDLE_TIMEOUT_MS - (Date.now() - lastActivityAtRef.current)
    if (remainingMs <= 0) {
      handleIdleLogout()
      return
    }

    idleTimerRef.current = window.setTimeout(() => {
      const latestStoredSession = readStoredSession()
      const latestActivityAt =
        latestStoredSession && latestStoredSession.token === storedSessionRef.current?.token
          ? Math.max(lastActivityAtRef.current, latestStoredSession.lastActivityAt)
          : lastActivityAtRef.current

      if (!storedSessionRef.current?.token || !latestActivityAt) {
        return
      }

      lastActivityAtRef.current = latestActivityAt
      const remainingAfterWake = AUTH_IDLE_TIMEOUT_MS - (Date.now() - latestActivityAt)
      if (remainingAfterWake <= 0) {
        handleIdleLogout()
        return
      }

      scheduleIdleLogout()
    }, remainingMs)
  })

  const flushSessionActivity = useEffectEvent(() => {
    const currentSession = storedSessionRef.current
    const latestActivityAt = lastActivityAtRef.current
    if (!currentSession?.token || latestActivityAt <= lastPersistedActivityAtRef.current) {
      return
    }

    const nextSession = { ...currentSession, lastActivityAt: latestActivityAt }
    persistSession(nextSession)
    storedSessionRef.current = nextSession
    lastPersistedActivityAtRef.current = latestActivityAt
    setStoredSession(nextSession)
  })

  const touchSessionActivity = useEffectEvent(() => {
    const currentSession = storedSessionRef.current
    if (!currentSession?.token) {
      return
    }

    const now = Date.now()
    lastActivityAtRef.current = now

    if (now - lastPersistedActivityAtRef.current >= SESSION_ACTIVITY_THROTTLE_MS) {
      const nextSession = { ...currentSession, lastActivityAt: now }
      persistSession(nextSession)
      storedSessionRef.current = nextSession
      lastPersistedActivityAtRef.current = now
      setStoredSession(nextSession)
    }

    scheduleIdleLogout()
  })

  async function loadPublicData(authToken?: string) {
    const requestId = ++loadPublicDataRequestIdRef.current
    try {
      const [systemResult, publicWordbookResult] = await Promise.allSettled([
        systemApi.getOverview(),
        searchApi.listPublicWordbooks(authToken ? authOptions(authToken) : undefined),
      ])

      if (requestId !== loadPublicDataRequestIdRef.current) {
        return
      }

      if (systemResult.status === 'fulfilled') {
        setOverview(systemResult.value)
      }
      if (publicWordbookResult.status === 'fulfilled') {
        const publicWordbookData = publicWordbookResult.value
        setPublicWordbooks(publicWordbookData)
        setSelectedPublicWordbookId((current) =>
          current && publicWordbookData.some((item) => item.id === current)
            ? current
            : (publicWordbookData[0]?.id ?? null),
        )
      } else {
        const publicWordbookMessage =
          publicWordbookResult.reason instanceof Error ? publicWordbookResult.reason.message : ''
        if (/401|unauthorized|token|login/i.test(publicWordbookMessage)) {
          setPublicWordbooks([])
          setSelectedPublicWordbookId(null)
        }
      }

      const firstFailedResult = [systemResult, publicWordbookResult].find((result) => {
        if (result.status !== 'rejected') {
          return false
        }
        const message = result.reason instanceof Error ? result.reason.message : ''
        return !/401|unauthorized|token|login/i.test(message)
      })
      if (firstFailedResult?.status === 'rejected') {
        setError(
          firstFailedResult.reason instanceof Error
            ? firstFailedResult.reason.message
            : '部分公开内容加载失败',
        )
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '公开内容初始化失败')
    }
  }

  async function loadPrivateData(nextPath?: string, authToken = token) {
    if (!authToken) return
    const requestId = ++loadPrivateDataRequestIdRef.current
    setLoading(true)
    try {
      const options = authOptions(authToken)
      const me = await authApi.me(options)
      if (requestId !== loadPrivateDataRequestIdRef.current) return
      setUser(me)
      setQuizOptionStrategy(me.quizOptionStrategy ?? 'RANDOM')

      const [systemResult, studyResult, progressResult, presetResult, wordbookResult, publicWordbookResult, wordNotebookResult] =
        await Promise.allSettled([
          systemApi.getOverview(),
          studyApi.getAgenda(options),
          studyApi.getProgress(options),
          importApi.listPresets(options),
          quizApi.listWordbooks(options),
          searchApi.listPublicWordbooks(options),
          wordNotebookApi.listWordNotebooks(options),
        ])

      const presetData = presetResult.status === 'fulfilled' ? presetResult.value : []
      const wordbookData = wordbookResult.status === 'fulfilled' ? wordbookResult.value : []
      const publicWordbookData = publicWordbookResult.status === 'fulfilled' ? publicWordbookResult.value : []
      const wordNotebookData = wordNotebookResult.status === 'fulfilled' ? wordNotebookResult.value : []
      if (requestId !== loadPrivateDataRequestIdRef.current) return

      if (systemResult.status === 'fulfilled') {
        setOverview(systemResult.value)
      }
      if (studyResult.status === 'fulfilled') {
        setAgenda(studyResult.value)
      }
      if (progressResult.status === 'fulfilled') {
        setProgress(progressResult.value)
      }
      setPresets(presetData)
      setWordbooks(wordbookData)
      setWordNotebooks(wordNotebookData)
      setPublicWordbooks(publicWordbookData)
      setSelectedPublicWordbookId((current) =>
        current && publicWordbookData.some((item) => item.id === current)
          ? current
          : (publicWordbookData[0]?.id ?? null),
      )
      setSelectedPlatform(
        presetData.find((preset) => preset.platform === 'ANKI')?.platform ?? DEFAULT_IMPORT_PLATFORM,
      )
      setSelectedWordbookId((current) =>
        current && wordbookData.some((item) => item.id === current) ? current : (wordbookData[0]?.id ?? null),
      )
      setSelectedWordNotebookId((current) =>
        current && wordNotebookData.some((item) => item.id === current) ? current : (wordNotebookData[0]?.id ?? null),
      )
      if (nextPath) {
        navigate(nextPath)
      }

      const firstFailedResult = [
        systemResult,
        studyResult,
        progressResult,
        presetResult,
        wordbookResult,
        publicWordbookResult,
        wordNotebookResult,
      ].find((result) => result.status === 'rejected')

      if (firstFailedResult?.status === 'rejected') {
        setError(
          firstFailedResult.reason instanceof Error ? firstFailedResult.reason.message : '部分工作区数据加载失败',
        )
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '工作区初始化失败')
    } finally {
      if (requestId === loadPrivateDataRequestIdRef.current) {
        setLoading(false)
      }
    }
  }

  useEffect(() => {
    if (!error && !message && !flashNotice) return

    const timer = window.setTimeout(() => {
      setError((current) => (current === error ? null : current))
      setMessage((current) => (current === message ? null : current))
      setFlashNotice((current) => (current === flashNotice ? null : current))
    }, 3800)

    return () => window.clearTimeout(timer)
  }, [error, message, flashNotice])

  function showGlobalNotice(text: string, type: 'success' | 'error' = 'success') {
    setError(null)
    setMessage(null)
    setFlashNotice({ type, text })
  }

  useEffect(() => {
    storedSessionRef.current = storedSession

    if (!storedSession) {
      clearIdleTimer()
      return
    }

    lastActivityAtRef.current = Math.max(lastActivityAtRef.current, storedSession.lastActivityAt)
    lastPersistedActivityAtRef.current = storedSession.lastActivityAt
  }, [storedSession])

  useEffect(() => {
    const sessionId = quizState?.session.id
    if (sessionId) {
      persistQuizSessionId(sessionId)
    }
  }, [quizState?.session.id])

  useEffect(() => {
    if (!token) {
      clearIdleTimer()
      return
    }

    scheduleIdleLogout()
    return clearIdleTimer
  }, [token])

  useEffect(() => {
    if (!token) {
      return
    }

    const handleActivity = () => {
      touchSessionActivity()
    }

    for (const eventName of SESSION_ACTIVITY_EVENTS) {
      window.addEventListener(eventName, handleActivity, { passive: true })
    }

    return () => {
      for (const eventName of SESSION_ACTIVITY_EVENTS) {
        window.removeEventListener(eventName, handleActivity)
      }
    }
  }, [token])

  useEffect(() => {
    if (!token) {
      hasTrackedRouteActivityRef.current = false
      return
    }

    if (!hasTrackedRouteActivityRef.current) {
      hasTrackedRouteActivityRef.current = true
      return
    }

    touchSessionActivity()
  }, [token, location.pathname, location.search])

  useEffect(() => {
    if (!token) {
      return
    }

    const handlePageHide = () => {
      flushSessionActivity()
    }

    window.addEventListener('pagehide', handlePageHide)
    return () => {
      window.removeEventListener('pagehide', handlePageHide)
      flushSessionActivity()
    }
  }, [token])

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        await loadPublicData(token || undefined)
        if (!alive) return
        if (token) {
          await loadPrivateData()
        }
      } catch (err) {
        if (alive) {
          setError(err instanceof Error ? err.message : '应用初始化失败')
        }
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    if (!token || quizState || creatingQuiz || location.pathname !== '/quiz') {
      return
    }

    const sessionId = readStoredQuizSessionId()
    if (!sessionId) {
      return
    }

    let cancelled = false
    ;(async () => {
      try {
        const restored = await quizApi.getSessionState(sessionId, authOptions())
        if (cancelled) {
          return
        }
        if (restored.session.targetType === 'PUBLIC_WORDBOOK') {
          setSelectedPublicWordbookId(restored.session.targetId)
        }
        if (restored.session.targetType === 'WORD_NOTEBOOK') {
          setSelectedWordNotebookId(restored.session.targetId)
        }
        if (restored.session.targetType === 'USER_WORDBOOK') {
          setSelectedWordbookId(restored.session.targetId)
        }
        setQuizState(restored)
      } catch {
        if (!cancelled) {
          clearStoredQuizSessionId()
          setQuizState(null)
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [token, location.pathname, quizState, creatingQuiz])

  useEffect(() => {
    if (!token || !selectedWordbookId) {
      setEntries([])
      setWordbookProgress(null)
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        const options = authOptions()
        const [entryData, progressData] = await Promise.all([
          quizApi.listWordbookEntries(selectedWordbookId, options),
          quizApi.getWordbookProgress(selectedWordbookId, options),
        ])
        if (!cancelled) {
          setEntries(entryData)
          setWordbookProgress(progressData)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '词书加载失败')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [token, selectedWordbookId])

  useEffect(() => {
    if (!token || !selectedWordNotebookId) {
      setWordNotebookEntries([])
      setWordNotebookEntriesLoading(false)
      return
    }

    let cancelled = false
    setWordNotebookEntries([])
    setWordNotebookEntriesLoading(true)
    ;(async () => {
      try {
        const notebookEntries = await wordNotebookApi.listWordNotebookEntries(selectedWordNotebookId, authOptions())
        if (!cancelled) {
          setWordNotebookEntries(notebookEntries)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '单词本加载失败')
        }
      } finally {
        if (!cancelled) {
          setWordNotebookEntriesLoading(false)
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [token, selectedWordNotebookId])

  useEffect(() => {
    if (!token) return
    let cancelled = false
    const query = deferredSearchQuery.trim()

    if (!query) {
      setSearchResult({ hits: [] })
      return
    }

    ;(async () => {
      try {
        const result = await searchApi.searchWords(query, authOptions())
        if (!cancelled) {
          setSearchResult(result)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '搜索失败')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [token, deferredSearchQuery])

  useEffect(() => {
    if (!token || !selectedWordbookId) {
      setLibrarySearchResult({ hits: [] })
      return
    }

    let cancelled = false
    const query = deferredLibrarySearchQuery.trim()

    if (!query) {
      setLibrarySearchResult({ hits: [] })
      return
    }

    ;(async () => {
      try {
        const result = await searchApi.searchWords(query, authOptions(), selectedWordbookId)
        if (!cancelled) {
          setLibrarySearchResult(result)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '词书内搜索失败')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [token, selectedWordbookId, deferredLibrarySearchQuery])

  useEffect(() => {
    if (!token) return
    const query = searchQuery.trim()
    if (!query) {
      setSearchSuggestions([])
      return
    }

    let cancelled = false
    const timer = window.setTimeout(async () => {
      try {
        const suggestions = await searchApi.searchSuggestions(query, authOptions())
        if (!cancelled) {
          setSearchSuggestions(suggestions)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setSearchSuggestions([])
          setError(err instanceof Error ? err.message : '联想词加载失败')
        }
      }
    }, 160)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [token, searchQuery])

  useEffect(() => {
    if (!token || !selectedWordbookId) {
      setLibrarySearchSuggestions([])
      return
    }

    const query = librarySearchQuery.trim()
    if (!query) {
      setLibrarySearchSuggestions([])
      return
    }

    let cancelled = false
    const timer = window.setTimeout(async () => {
      try {
        const suggestions = await searchApi.searchSuggestions(query, authOptions(), selectedWordbookId)
        if (!cancelled) {
          setLibrarySearchSuggestions(suggestions)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setLibrarySearchSuggestions([])
          setError(err instanceof Error ? err.message : '词书联想词加载失败')
        }
      }
    }, 160)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [token, selectedWordbookId, librarySearchQuery])

  async function handleLogin() {
    if (loading) return
    setError(null)
    setLoading(true)
    try {
      const result = await authApi.login({ account: account.trim(), password: loginPassword })
      const session = createStoredSession(result.accessToken)
      storeSession(session)
      setUser(result.user)
      setQuizOptionStrategy(result.user.quizOptionStrategy ?? 'RANDOM')
      scheduleIdleLogout()
      await loadPrivateData(undefined, result.accessToken)
      setAuthModalOpen(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
    } finally {
      setLoading(false)
    }
  }

  async function handleRegister() {
    if (loading) return
    setError(null)
    setLoading(true)
    try {
      const result = await authApi.register({
        username: registerUsername.trim(),
        email: registerEmail.trim(),
        password: registerPassword,
      })
      const session = createStoredSession(result.accessToken)
      storeSession(session)
      setUser(result.user)
      setQuizOptionStrategy(result.user.quizOptionStrategy ?? 'RANDOM')
      setSourceName(`${result.user.username}-词书`)
      scheduleIdleLogout()
      await loadPrivateData(undefined, result.accessToken)
      setAuthModalOpen(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败')
    } finally {
      setLoading(false)
    }
  }

  async function handleUpdateProfile(payload: { username: string; avatarUrl: string | null }) {
    if (!token) {
      openAuthModal()
      return
    }
    setError(null)
    setMessage(null)
    setLoading(true)
    try {
      const result = await authApi.updateProfile(payload, authOptions())
      const session = createStoredSession(result.accessToken)
      storeSession(session)
      setUser(result.user)
      setQuizOptionStrategy(result.user.quizOptionStrategy ?? 'RANDOM')
      scheduleIdleLogout()
      setMessage('个人资料已更新')
    } catch (err) {
      setError(err instanceof Error ? err.message : '个人资料更新失败')
      throw err
    } finally {
      setLoading(false)
    }
  }

  async function handleUploadAvatar(file: File) {
    if (!token) {
      openAuthModal()
      return
    }
    setError(null)
    setMessage(null)
    setLoading(true)
    try {
      const result = await authApi.uploadAvatar(file, authOptions())
      const session = createStoredSession(result.accessToken)
      storeSession(session)
      setUser(result.user)
      setQuizOptionStrategy(result.user.quizOptionStrategy ?? 'RANDOM')
      scheduleIdleLogout()
      setMessage('头像已更新')
    } catch (err) {
      setError(err instanceof Error ? err.message : '头像上传失败')
      throw err
    } finally {
      setLoading(false)
    }
  }

  async function handleImport() {
    if (!token) {
      openAuthModal()
      return
    }
    if (!selectedFile) {
      setError('请先选择导入文件')
      return
    }
    setError(null)
    setMessage(null)
    try {
      const task = await importApi.importFile(
        {
          platform: selectedPlatform,
          sourceName,
          file: selectedFile,
        },
        authOptions(),
      )
      setMessage(`导入完成，新增 ${task.importedCards} 条词条。`)
      setSelectedFile(null)
      await loadPrivateData()
      if (task.wordbookId) {
        setSelectedWordbookId(task.wordbookId)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败')
    }
  }

  async function handleCreateQuiz(targetType: QuizTargetType = 'USER_WORDBOOK', targetId?: number) {
    return handleCreateQuizForTarget(targetType, targetId)
    /*
    if (!token) {
      openAuthModal()
      return
    }
    const defaultWordbookId = selectedWordbookId ?? wordbooks[0]?.id ?? null
    const resolvedTargetId =
      targetId ?? (targetType === 'PUBLIC_WORDBOOK' ? selectedPublicWordbookId : defaultWordbookId)
    if (!resolvedTargetId && targetType === 'USER_WORDBOOK') {
      setError('请选择一本词书')
      navigate('/')
      return
    }
    if (!resolvedTargetId) {
      setError(targetType === 'PUBLIC_WORDBOOK' ? '请先选择一本公共词书' : '请先选择一本词书')
      return
    }
    const finalTargetId = Number(resolvedTargetId)
    const targetNotebook =
      targetType === 'WORD_NOTEBOOK' ? (wordNotebooks.find((item) => item.id === finalTargetId) ?? null) : null

    if (targetType === 'WORD_NOTEBOOK' && targetNotebook && targetNotebook.wordCount <= 0) {
      showGlobalNotice('当前单词本中无单词', 'error')
      setSelectedWordNotebookId(finalTargetId)
      return
    }

    const finalTargetId = Number(resolvedTargetId)

    if (creatingQuiz) {
      return
    }
    setError(null)
    setMessage('正在创建练习...')
    clearStoredQuizSessionId()
    setQuizState(null)
    setCreatingQuiz(true)
    try {
      const result = await quizApi.createSession(
        { targetType, targetId: finalTargetId, mode: targetType === 'PUBLIC_WORDBOOK' ? 'EN_TO_CN' : quizMode },
        authOptions(),
      )
      if (targetType === 'PUBLIC_WORDBOOK') {
        setSelectedPublicWordbookId(finalTargetId)
      } else {
        setSelectedWordbookId(resolvedTargetId)
      }
      setQuizState(result)
      setMessage(null)
      navigate('/quiz')
    } catch (err) {
      const message = err instanceof Error ? err.message : '练习启动失败'
      setError(message)
      setMessage(null)
    } finally {
      setCreatingQuiz(false)
    }
    */
  }

  async function handleCreateQuizForTarget(targetType: QuizTargetType = 'USER_WORDBOOK', targetId?: number) {
    if (!token) {
      openAuthModal()
      return
    }

    const defaultWordbookId = selectedWordbookId ?? wordbooks[0]?.id ?? null
    const defaultNotebookId = selectedWordNotebookId ?? wordNotebooks[0]?.id ?? null
    const resolvedTargetId =
      targetId ??
      (targetType === 'PUBLIC_WORDBOOK'
        ? selectedPublicWordbookId
        : targetType === 'WORD_NOTEBOOK'
          ? defaultNotebookId
          : defaultWordbookId)

    if (!resolvedTargetId && targetType === 'USER_WORDBOOK') {
      setError('请先选择一本词书')
      navigate('/')
      return
    }

    if (!resolvedTargetId) {
      setError(
        targetType === 'PUBLIC_WORDBOOK'
          ? '请先选择一本公共词书'
          : targetType === 'WORD_NOTEBOOK'
            ? '请先选择一个单词本'
            : '请先选择一本词书',
      )
      return
    }

    const finalTargetId = Number(resolvedTargetId)
    const targetNotebook =
      targetType === 'WORD_NOTEBOOK' ? (wordNotebooks.find((item) => item.id === finalTargetId) ?? null) : null

    if (targetType === 'WORD_NOTEBOOK' && targetNotebook && targetNotebook.wordCount <= 0) {
      setSelectedWordNotebookId(finalTargetId)
      showGlobalNotice('当前单词本还没有单词，请先收藏单词后再查看或背词。', 'error')
      return
    }

    if (creatingQuiz) {
      return
    }

    setError(null)
    setMessage('正在创建练习...')
    clearStoredQuizSessionId()
    setQuizState(null)
    setCreatingQuiz(true)
    try {
      if (targetType === 'PUBLIC_WORDBOOK') {
        setSelectedPublicWordbookId(finalTargetId)
      } else if (targetType === 'WORD_NOTEBOOK') {
        setSelectedWordNotebookId(finalTargetId)
      } else {
        setSelectedWordbookId(finalTargetId)
      }
      const result = await quizApi.createSession(
        {
          targetType,
          targetId: finalTargetId,
          mode: targetType === 'PUBLIC_WORDBOOK' || targetType === 'WORD_NOTEBOOK' ? 'EN_TO_CN' : quizMode,
        },
        authOptions(),
      )
      setQuizState(result)
      setMessage(null)
      navigate('/quiz')
    } catch (err) {
      setMessage(null)
      const nextError = err instanceof Error ? err.message : '练习启动失败'
      if (targetType === 'WORD_NOTEBOOK' && /no available words/i.test(nextError)) {
        showGlobalNotice('当前单词本还没有单词，请先收藏单词后再查看或背词。', 'error')
      } else {
        setError(nextError)
      }
    } finally {
      setCreatingQuiz(false)
    }
  }

  async function handleUpdateQuizOptionStrategy(nextStrategy: QuizOptionStrategy, currentAttemptId?: number) {
    if (!token) {
      openAuthModal()
      return
    }
    const previousStrategy = quizOptionStrategy
    setError(null)
    setQuizOptionStrategy(nextStrategy)
    try {
      const updatedUser = await authApi.updateQuizOptionStrategy(
        { quizOptionStrategy: nextStrategy },
        authOptions(),
      )
      setUser(updatedUser)
      setQuizOptionStrategy(updatedUser.quizOptionStrategy ?? nextStrategy)
      setQuizState((current) =>
        current
          ? {
              ...current,
              session: {
                ...current.session,
                quizOptionStrategy: updatedUser.quizOptionStrategy ?? nextStrategy,
              },
            }
          : current,
      )
      if (currentAttemptId != null && quizState?.session.id && quizState.currentQuestion?.attemptId === currentAttemptId) {
        const refreshed = await quizApi.refreshQuestionOptions(
          quizState.session.id,
          currentAttemptId,
          authOptions(),
        )
        window.setTimeout(() => setQuizState(refreshed), 0)
      }
    } catch (err) {
      setQuizOptionStrategy(previousStrategy)
      setError(err instanceof Error ? err.message : '答题选项保存失败')
    }
  }

  async function handleAnswer(option: string): Promise<QuizAnswerResult | null> {
    if (!quizState?.currentQuestion) return null
    setError(null)
    try {
      const result = await quizApi.answerQuestion(
        quizState.session.id,
        {
          attemptId: quizState.currentQuestion.attemptId,
          selectedOption: option,
        },
        authOptions(),
      )
      if (result.publicWordbookProgress && result.session.targetType === 'PUBLIC_WORDBOOK') {
        syncPublicWordbookProgress({
          ...result.publicWordbookProgress,
          todayCorrectAttempts: result.session.todayCorrectAttempts,
          todayTotalAttempts: result.session.todayTotalAttempts,
        })
      }
      return result
    } catch (err) {
      setError(err instanceof Error ? err.message : '答案提交失败')
      return null
    }
  }

  async function advanceQuiz(result: QuizAnswerResult) {
    try {
      const nextState = await quizApi.getSessionState(result.session.id, authOptions())
      if (nextState.session.targetType === 'PUBLIC_WORDBOOK') {
        setSelectedPublicWordbookId(nextState.session.targetId)
      }
      if (nextState.session.targetType === 'WORD_NOTEBOOK') {
        setSelectedWordNotebookId(nextState.session.targetId)
      }
      if (nextState.session.targetType === 'USER_WORDBOOK') {
        setSelectedWordbookId(nextState.session.targetId)
      }
      setQuizState(nextState)
    } catch (err) {
      const message = err instanceof Error ? err.message : '涓嬩竴棰樺姞杞藉け璐?'
      if (result.remainingQuestions <= 0 || /no longer active|not found|session/i.test(message)) {
        setQuizState({ session: result.session, currentQuestion: null })
      } else {
        setError(message)
      }
    } finally {
      void loadPrivateData()
    }
  }

  async function getWordDetail(entryId: number, entryType: SearchEntryType = 'PUBLIC') {
    return searchApi.getWordDetailByType(entryId, entryType, authOptions())
  }

  async function handleSubscribePublicWordbook(publicWordbookId = selectedPublicWordbookId) {
    if (!token) {
      openAuthModal()
      return
    }
    if (!publicWordbookId) {
      setError('请先选择一本公共词书')
      return
    }
    setError(null)
    setMessage('正在订阅公共词书...')
    setSubscribingPublicWordbookId(publicWordbookId)
    try {
      const subscribed = await searchApi.subscribePublicWordbook(publicWordbookId, authOptions())
      setMessage(`已订阅 ${subscribed.name}。`)
      await loadPrivateData('/library')
      setSelectedPublicWordbookId(subscribed.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : '公共词书订阅失败')
      setMessage(null)
    } finally {
      setSubscribingPublicWordbookId(null)
    }
  }

  async function handleUnsubscribePublicWordbook(publicWordbookId: number) {
    if (!token) {
      openAuthModal()
      return
    }
    if (!publicWordbookId) {
      setError('请先选择一本公共词书')
      return
    }
    setError(null)
    setMessage('正在取消订阅...')
    setUnsubscribingPublicWordbookId(publicWordbookId)
    try {
      const unsubscribed = await searchApi.unsubscribePublicWordbook(publicWordbookId, authOptions())
      setMessage(`已取消订阅 ${unsubscribed.name}。`)
      if (quizState?.session.targetType === 'PUBLIC_WORDBOOK' && quizState.session.targetId === publicWordbookId) {
        setQuizState(null)
      }
      await loadPrivateData('/library')
      setSelectedPublicWordbookId((current) => (current === publicWordbookId ? unsubscribed.id : current))
    } catch (err) {
      setError(err instanceof Error ? err.message : '取消订阅失败')
      setMessage(null)
    } finally {
      setUnsubscribingPublicWordbookId(null)
    }
  }

  async function handleResetPublicWordbookProgress(publicWordbookId = selectedPublicWordbookId) {
    if (!token) {
      openAuthModal()
      return
    }
    if (!publicWordbookId) {
      setError('请先选择一本公共词书')
      return
    }
    setError(null)
    setMessage('正在重置公共词书进度...')
    setResettingPublicWordbookId(publicWordbookId)
    try {
      const reset = await searchApi.resetPublicWordbookProgress(publicWordbookId, authOptions())
      setMessage(`已重置 ${reset.name} 的进度。`)
      if (quizState?.session.targetType === 'PUBLIC_WORDBOOK' && quizState.session.targetId === publicWordbookId) {
        setQuizState(null)
      }
      await loadPrivateData('/library')
      setSelectedPublicWordbookId(reset.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : '公共词书进度重置失败')
      setMessage(null)
    } finally {
      setResettingPublicWordbookId(null)
    }
  }

  async function handleUpdatePublicWordbookDailyTarget(publicWordbookId: number, dailyTargetCount: number) {
    if (!token) {
      openAuthModal()
      return null
    }
    if (!publicWordbookId) {
      setError('请先选择一本公共词书')
      return null
    }
    setError(null)
    setMessage(null)
    try {
      const updated = await searchApi.updatePublicWordbookDailyTarget(
        publicWordbookId,
        { dailyTargetCount },
        authOptions(),
      )
      setMessage(`每日背词数量已更新为 ${updated.dailyTargetCount} 个。`)
      await loadPrivateData('/library')
      setSelectedPublicWordbookId(updated.id)
      return updated
    } catch (err) {
      setError(err instanceof Error ? err.message : '每日背词数量更新失败')
      return null
    }
  }

  async function handleCreateWordNotebook(name: string) {
    if (!token) {
      openAuthModal()
      return null
    }

    setError(null)
    setMessage(null)
    try {
      const created = await wordNotebookApi.createWordNotebook({ name }, authOptions())
      setWordNotebooks((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setSelectedWordNotebookId(created.id)
      setMessage(`已创建单词本 ${created.name}`)
      return created
    } catch (err) {
      setError(err instanceof Error ? err.message : '单词本创建失败')
      return null
    }
  }

  async function handleAddWordNotebookEntry(notebookId: number, payload: AddWordNotebookEntryRequest) {
    if (!token) {
      openAuthModal()
      return null
    }

    setError(null)
    setMessage(null)
    try {
      const result = await wordNotebookApi.addWordNotebookEntry(notebookId, payload, authOptions())
      setWordNotebooks((current) => [result.notebook, ...current.filter((item) => item.id !== result.notebook.id)])
      setSelectedWordNotebookId((current) => current ?? result.notebook.id)
      setWordNotebookEntries((current) => {
        if (selectedWordNotebookId !== result.notebook.id) {
          return current
        }
        return [result.entry, ...current.filter((item) => item.id !== result.entry.id)]
      })
      setMessage(result.added ? `已加入 ${result.notebook.name}` : `${result.entry.word} 已在 ${result.notebook.name} 中`)
      return result
    } catch (err) {
      setError(err instanceof Error ? err.message : '收藏单词失败')
      return null
    }
  }

  async function handleRemoveWordNotebookEntry(word: string) {
    if (!token) {
      openAuthModal()
      return null
    }

    const targetWord = word.trim()
    if (!targetWord) {
      return null
    }

    setError(null)
    setMessage(null)
    try {
      const removedCount = await wordNotebookApi.removeWordFromNotebooks(targetWord, authOptions())
      const notebookData = await wordNotebookApi.listWordNotebooks(authOptions())
      setWordNotebooks(notebookData)

      const nextSelectedNotebookId =
        selectedWordNotebookId && notebookData.some((item) => item.id === selectedWordNotebookId)
          ? selectedWordNotebookId
          : (notebookData[0]?.id ?? null)

      setSelectedWordNotebookId(nextSelectedNotebookId)

      if (nextSelectedNotebookId) {
        setWordNotebookEntriesLoading(true)
        const notebookEntries = await wordNotebookApi.listWordNotebookEntries(nextSelectedNotebookId, authOptions())
        setWordNotebookEntries(notebookEntries)
      } else {
        setWordNotebookEntries([])
      }
      setWordNotebookEntriesLoading(false)

      setFlashNotice({ type: 'success', text: `已取消收藏 ${targetWord}` })
      return removedCount
    } catch (err) {
      setWordNotebookEntriesLoading(false)
      setError(err instanceof Error ? err.message : '取消收藏失败')
      return null
    }
  }

  async function refreshWordNotebookState(preferredNotebookId?: number | null) {
    void handleCreateWordNotebook
    void handleAddWordNotebookEntry
    void handleRemoveWordNotebookEntry

    const notebookData = await wordNotebookApi.listWordNotebooks(authOptions())
    setWordNotebooks(notebookData)

    const nextSelectedNotebookId =
      preferredNotebookId && notebookData.some((item) => item.id === preferredNotebookId)
        ? preferredNotebookId
        : selectedWordNotebookId && notebookData.some((item) => item.id === selectedWordNotebookId)
          ? selectedWordNotebookId
          : (notebookData[0]?.id ?? null)

    setSelectedWordNotebookId(nextSelectedNotebookId)

    if (nextSelectedNotebookId) {
      setWordNotebookEntriesLoading(true)
      try {
        const notebookEntries = await wordNotebookApi.listWordNotebookEntries(nextSelectedNotebookId, authOptions())
        setWordNotebookEntries(notebookEntries)
      } finally {
        setWordNotebookEntriesLoading(false)
      }
    } else {
      setWordNotebookEntries([])
      setWordNotebookEntriesLoading(false)
    }

    return { notebookData, nextSelectedNotebookId }
  }

  async function createWordNotebookAction(name: string) {
    if (!token) {
      openAuthModal()
      return null
    }

    const notebookName = name.trim()
    if (!notebookName) {
      setError('单词本名称不能为空')
      return null
    }

    setError(null)
    setMessage(null)
    try {
      const created = await wordNotebookApi.createWordNotebook({ name: notebookName }, authOptions())
      await refreshWordNotebookState(created.id)
      setFlashNotice({ type: 'success', text: `已创建单词本 ${created.name}` })
      return created
    } catch (err) {
      setError(err instanceof Error ? `创建单词本失败：${err.message}` : `创建单词本失败：${notebookName}`)
      return null
    }
  }

  async function addWordNotebookEntryAction(notebookId: number, payload: AddWordNotebookEntryRequest) {
    if (!token) {
      openAuthModal()
      return null
    }

    setError(null)
    setMessage(null)
    try {
      const result = await wordNotebookApi.addWordNotebookEntry(notebookId, payload, authOptions())
      setWordNotebooks((current) => [result.notebook, ...current.filter((item) => item.id !== result.notebook.id)])
      setSelectedWordNotebookId(result.notebook.id)
      setWordNotebookEntries((current) => {
        if (selectedWordNotebookId !== result.notebook.id && selectedWordNotebookId != null) {
          return current
        }
        return [result.entry, ...current.filter((item) => item.id !== result.entry.id)]
      })
      setMessage(result.added ? `已加入 ${result.notebook.name}` : `${result.entry.word} 已在 ${result.notebook.name} 中`)
      return result
    } catch (err) {
      setError(err instanceof Error ? `收藏单词失败：${err.message}` : '收藏单词失败')
      return null
    }
  }

  async function removeWordNotebookEntryAction(word: string) {
    if (!token) {
      openAuthModal()
      return null
    }

    const targetWord = word.trim()
    if (!targetWord) {
      return null
    }

    setError(null)
    setMessage(null)
    try {
      const removedCount = await wordNotebookApi.removeWordFromNotebooks(targetWord, authOptions())
      await refreshWordNotebookState()
      setFlashNotice({ type: 'success', text: `已取消收藏 ${targetWord}` })
      return removedCount
    } catch (err) {
      setError(err instanceof Error ? `取消收藏失败：${err.message}` : '取消收藏失败')
      return null
    }
  }

  function pickSearchSuggestion(value: string) {
    setSearchSuggestions([])
    setSearchQuery(value)
  }

  function pickLibrarySearchSuggestion(value: string) {
    setLibrarySearchSuggestions([])
    setLibrarySearchQuery(value)
  }

  const preset = presets.find((item) => item.platform === selectedPlatform)
  const selectedWordbook = wordbooks.find((item) => item.id === selectedWordbookId) ?? null
  const selectedWordNotebook = wordNotebooks.find((item) => item.id === selectedWordNotebookId) ?? null
  const selectedPublicWordbook = publicWordbooks.find((item) => item.id === selectedPublicWordbookId) ?? null

  return {
    token,
    user,
    authModalOpen,
    openAuthModal,
    openAuthModalIfAllowed,
    closeAuthModal,
    clearAuthError,
    layoutMode,
    setLayoutMode,
    clearAuth,
    authTab,
    setAuthTab,
    overview,
    agenda,
    progress,
    presets,
    wordbooks,
    entries,
    wordNotebooks,
    selectedWordNotebookId,
    setSelectedWordNotebookId,
    selectedWordNotebook,
    wordNotebookEntries,
    wordNotebookEntriesLoading,
    publicWordbooks,
    selectedPublicWordbookId,
    setSelectedPublicWordbookId,
    selectedPublicWordbook,
    subscribingPublicWordbookId,
    unsubscribingPublicWordbookId,
    resettingPublicWordbookId,
    wordbookProgress,
    selectedWordbookId,
    setSelectedWordbookId,
    searchQuery,
    setSearchQuery,
    searchResult,
    searchSuggestions,
    pickSearchSuggestion,
    librarySearchQuery,
    setLibrarySearchQuery,
    librarySearchResult,
    librarySearchSuggestions,
    pickLibrarySearchSuggestion,
    quizMode,
    setQuizMode,
    quizOptionStrategy,
    handleUpdateQuizOptionStrategy,
    quizState,
    creatingQuiz,
    message,
    error,
    flashNotice,
    showGlobalNotice,
    loading,
    account,
    setAccount,
    loginPassword,
    setLoginPassword,
    registerUsername,
    setRegisterUsername,
    registerEmail,
    setRegisterEmail,
    registerPassword,
    setRegisterPassword,
    sourceName,
    setSourceName,
    selectedPlatform,
    setSelectedPlatform,
    selectedFile,
    setSelectedFile,
    preset,
    selectedWordbook,
    handleLogin,
    handleRegister,
    handleUpdateProfile,
    handleUploadAvatar,
    handleImport,
    handleSubscribePublicWordbook,
    handleUnsubscribePublicWordbook,
    handleResetPublicWordbookProgress,
    handleUpdatePublicWordbookDailyTarget,
    handleCreateWordNotebook: createWordNotebookAction,
    handleAddWordNotebookEntry: addWordNotebookEntryAction,
    handleRemoveWordNotebookEntry: removeWordNotebookEntryAction,
    handleCreateQuiz,
    handleAnswer,
    advanceQuiz,
    getWordDetail,
  }
}
