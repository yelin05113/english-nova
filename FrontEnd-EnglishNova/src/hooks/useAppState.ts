import { useDeferredValue, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { ApiAuthOptions } from '../api/client'
import { authApi, type AuthUser } from '../api/modules/auth'
import { importApi, type ImportPlatform, type ImportPreset } from '../api/modules/imports'
import {
  quizApi,
  type QuizAnswerResult,
  type QuizMode,
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
import { DEFAULT_IMPORT_PLATFORM, TOKEN_KEY, TOKEN_TTL_MS } from '../constants'

interface StoredAuthSession {
  token: string
  expiresAt: number
}

function readStoredSession(): StoredAuthSession | null {
  localStorage.removeItem(TOKEN_KEY)

  const raw = sessionStorage.getItem(TOKEN_KEY)
  if (!raw) {
    return null
  }

  try {
    const session = JSON.parse(raw) as StoredAuthSession
    if (!session.token || typeof session.expiresAt !== 'number' || session.expiresAt <= Date.now()) {
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
    expiresAt: Date.now() + TOKEN_TTL_MS,
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

export function useAppState() {
  const navigate = useNavigate()
  const location = useLocation()
  const [storedSession, setStoredSession] = useState<StoredAuthSession | null>(readStoredSession)
  const [token, setToken] = useState(() => readStoredSession()?.token ?? '')
  const [tokenExpiresAt, setTokenExpiresAt] = useState(() => readStoredSession()?.expiresAt ?? 0)
  const [user, setUser] = useState<AuthUser | null>(null)

  const [authTab, setAuthTab] = useState<'login' | 'register'>('login')

  const [overview, setOverview] = useState<SystemOverview | null>(null)
  const [agenda, setAgenda] = useState<StudyAgenda | null>(null)
  const [progress, setProgress] = useState<StudyProgress | null>(null)
  const [presets, setPresets] = useState<ImportPreset[]>([])
  const [wordbooks, setWordbooks] = useState<WordbookSummary[]>([])
  const [entries, setEntries] = useState<VocabularyEntry[]>([])
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
  const [quizState, setQuizState] = useState<QuizSessionState | null>(null)
  const [creatingQuiz, setCreatingQuiz] = useState(false)
  const [subscribingPublicWordbookId, setSubscribingPublicWordbookId] = useState<number | null>(null)
  const [unsubscribingPublicWordbookId, setUnsubscribingPublicWordbookId] = useState<number | null>(null)
  const [resettingPublicWordbookId, setResettingPublicWordbookId] = useState<number | null>(null)

  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const [account, setAccount] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [registerUsername, setRegisterUsername] = useState('')
  const [registerEmail, setRegisterEmail] = useState('')
  const [registerPassword, setRegisterPassword] = useState('')
  const [sourceName, setSourceName] = useState('')
  const [selectedPlatform, setSelectedPlatform] = useState<ImportPlatform>(DEFAULT_IMPORT_PLATFORM)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  function authOptions(authToken = token): ApiAuthOptions {
    return { token: authToken, onUnauthorized: clearAuth }
  }

  function clearAuth() {
    clearStoredToken()
    setStoredSession(null)
    setToken('')
    setTokenExpiresAt(0)
    setUser(null)
    navigate('/auth')
    setAgenda(null)
    setProgress(null)
    setPresets([])
    setWordbooks([])
    setEntries([])
    setPublicWordbooks([])
    setSelectedPublicWordbookId(null)
    setWordbookProgress(null)
    setSelectedWordbookId(null)
    setQuizState(null)
    setCreatingQuiz(false)
    setSubscribingPublicWordbookId(null)
    setUnsubscribingPublicWordbookId(null)
    setResettingPublicWordbookId(null)
    setSearchResult({ hits: [] })
    setSearchSuggestions([])
    setLibrarySearchQuery('')
    setLibrarySearchResult({ hits: [] })
    setLibrarySearchSuggestions([])
  }

  async function loadPrivateData(nextPath?: string, authToken = token) {
    if (!authToken) return
    setLoading(true)
    try {
      const options = authOptions(authToken)
      const me = await authApi.me(options)
      setUser(me)

      const [systemResult, studyResult, progressResult, presetResult, wordbookResult, publicWordbookResult] =
        await Promise.allSettled([
          systemApi.getOverview(),
          studyApi.getAgenda(options),
          studyApi.getProgress(options),
          importApi.listPresets(options),
          quizApi.listWordbooks(options),
          searchApi.listPublicWordbooks(options),
        ])

      const presetData = presetResult.status === 'fulfilled' ? presetResult.value : []
      const wordbookData = wordbookResult.status === 'fulfilled' ? wordbookResult.value : []
      const publicWordbookData = publicWordbookResult.status === 'fulfilled' ? publicWordbookResult.value : []

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
      if (nextPath) {
        navigate(nextPath)
      } else if (location.pathname === '/auth' || location.pathname === '/') {
        navigate('/library')
      }

      const firstFailedResult = [
        systemResult,
        studyResult,
        progressResult,
        presetResult,
        wordbookResult,
      ].find((result) => result.status === 'rejected')

      if (firstFailedResult?.status === 'rejected') {
        setError(
          firstFailedResult.reason instanceof Error
            ? firstFailedResult.reason.message
            : '部分工作区数据加载失败',
        )
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '工作区初始化失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!token) return

    const remainingMs = tokenExpiresAt - Date.now()
    if (remainingMs <= 0) {
      clearAuth()
      setError('登录已超过 30 分钟，请重新登录')
      return
    }

    const timer = window.setTimeout(() => {
      clearAuth()
      setError('登录已超过 30 分钟，请重新登录')
    }, remainingMs)

    return () => {
      window.clearTimeout(timer)
    }
  }, [token, tokenExpiresAt])

  useEffect(() => {
    if (storedSession) {
      setToken(storedSession.token)
      setTokenExpiresAt(storedSession.expiresAt)
      return
    }
    setToken('')
    setTokenExpiresAt(0)
  }, [storedSession])

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const system = await systemApi.getOverview()
        if (!alive) return
        setOverview(system)
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
      persistSession(session)
      setStoredSession(session)
      setUser(result.user)
      await loadPrivateData('/library', result.accessToken)
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
      persistSession(session)
      setStoredSession(session)
      setUser(result.user)
      setSourceName(`${result.user.username}-词书`)
      await loadPrivateData('/imports', result.accessToken)
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败')
    } finally {
      setLoading(false)
    }
  }

  async function handleUpdateProfile(payload: { username: string; avatarUrl: string | null }) {
    if (!token) return
    setError(null)
    setMessage(null)
    setLoading(true)
    try {
      const result = await authApi.updateProfile(payload, authOptions())
      const session = createStoredSession(result.accessToken)
      persistSession(session)
      setStoredSession(session)
      setToken(result.accessToken)
      setTokenExpiresAt(session.expiresAt)
      setUser(result.user)
      setMessage('个人资料已更新。')
    } catch (err) {
      setError(err instanceof Error ? err.message : '个人资料更新失败')
      throw err
    } finally {
      setLoading(false)
    }
  }

  async function handleUploadAvatar(file: File) {
    if (!token) return
    setError(null)
    setMessage(null)
    setLoading(true)
    try {
      const result = await authApi.uploadAvatar(file, authOptions())
      const session = createStoredSession(result.accessToken)
      persistSession(session)
      setStoredSession(session)
      setToken(result.accessToken)
      setTokenExpiresAt(session.expiresAt)
      setUser(result.user)
      setMessage('头像已更新。')
    } catch (err) {
      setError(err instanceof Error ? err.message : '头像上传失败')
      throw err
    } finally {
      setLoading(false)
    }
  }

  async function handleImport() {
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
      await loadPrivateData('/library')
      if (task.wordbookId) {
        setSelectedWordbookId(task.wordbookId)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败')
    }
  }

  async function handleCreateQuiz(targetType: QuizTargetType = 'USER_WORDBOOK', targetId?: number) {
    const resolvedTargetId =
      targetId ?? (targetType === 'PUBLIC_WORDBOOK' ? selectedPublicWordbookId : selectedWordbookId)
    if (!resolvedTargetId) {
      setError(targetType === 'PUBLIC_WORDBOOK' ? '请先选择一本公共词书' : '请先选择一本词书')
      return
    }
    if (creatingQuiz) {
      return
    }
    setError(null)
    setMessage('正在创建练习...')
    setCreatingQuiz(true)
    try {
      const result = await quizApi.createSession(
        { targetType, targetId: resolvedTargetId, mode: quizMode },
        authOptions(),
      )
      setQuizState(result)
      setMessage(null)
      navigate('/quiz')
    } catch (err) {
      setError(err instanceof Error ? err.message : '练习启动失败')
      setMessage(null)
    } finally {
      setCreatingQuiz(false)
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
      return result
    } catch (err) {
      setError(err instanceof Error ? err.message : '答案提交失败')
      return null
    }
  }

  function advanceQuiz(result: QuizAnswerResult) {
    setQuizState({ session: result.session, currentQuestion: result.nextQuestion })
    void loadPrivateData()
  }

  async function getWordDetail(entryId: number, entryType: SearchEntryType = 'PUBLIC') {
    return searchApi.getWordDetailByType(entryId, entryType, authOptions())
  }

  async function handleSubscribePublicWordbook(publicWordbookId = selectedPublicWordbookId) {
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
  const selectedPublicWordbook = publicWordbooks.find((item) => item.id === selectedPublicWordbookId) ?? null

  return {
    token,
    user,
    clearAuth,
    authTab,
    setAuthTab,
    overview,
    agenda,
    progress,
    presets,
    wordbooks,
    entries,
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
    quizState,
    creatingQuiz,
    message,
    error,
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
    handleCreateQuiz,
    handleAnswer,
    advanceQuiz,
    getWordDetail,
  }
}
