import { useDeferredValue, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { apiFetch } from '../api/client'
import { DEFAULT_IMPORT_PLATFORM, TOKEN_KEY } from '../constants'
import type {
  AuthTokenResponse,
  AuthUser,
  ImportPlatform,
  ImportPreset,
  ImportTask,
  QuizAnswerResult,
  QuizMode,
  QuizSessionState,
  SearchSuggestion,
  StudyAgenda,
  StudyProgress,
  SystemOverview,
  WordDetail,
  VocabularyEntry,
  WordSearchResponse,
  WordbookProgress,
  WordbookSummary,
} from '../types/types'

export function useAppState() {
  const navigate = useNavigate()
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) ?? '')
  const [user, setUser] = useState<AuthUser | null>(null)

  const [authTab, setAuthTab] = useState<'login' | 'register'>('login')

  const [overview, setOverview] = useState<SystemOverview | null>(null)
  const [agenda, setAgenda] = useState<StudyAgenda | null>(null)
  const [progress, setProgress] = useState<StudyProgress | null>(null)
  const [presets, setPresets] = useState<ImportPreset[]>([])
  const [tasks, setTasks] = useState<ImportTask[]>([])
  const [wordbooks, setWordbooks] = useState<WordbookSummary[]>([])
  const [entries, setEntries] = useState<VocabularyEntry[]>([])
  const [wordbookProgress, setWordbookProgress] = useState<WordbookProgress | null>(null)
  const [selectedWordbookId, setSelectedWordbookId] = useState<number | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const deferredSearchQuery = useDeferredValue(searchQuery)
  const [searchResult, setSearchResult] = useState<WordSearchResponse>({ publicHits: [], myHits: [] })
  const [searchSuggestions, setSearchSuggestions] = useState<SearchSuggestion[]>([])
  const [quizMode, setQuizMode] = useState<QuizMode>('MIXED')
  const [quizState, setQuizState] = useState<QuizSessionState | null>(null)

  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const [account, setAccount] = useState('demo')
  const [loginPassword, setLoginPassword] = useState('123456')
  const [registerUsername, setRegisterUsername] = useState('')
  const [registerEmail, setRegisterEmail] = useState('')
  const [registerPassword, setRegisterPassword] = useState('')
  const [sourceName, setSourceName] = useState('')
  const [selectedPlatform, setSelectedPlatform] = useState<ImportPlatform>(DEFAULT_IMPORT_PLATFORM)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  function api<T>(path: string, init?: RequestInit, requireAuth = false, authToken = token) {
    return apiFetch<T>(path, init, { requireAuth, token: authToken, onUnauthorized: clearAuth })
  }

  function clearAuth() {
    localStorage.removeItem(TOKEN_KEY)
    setToken('')
    setUser(null)
    navigate('/auth')
    setAgenda(null)
    setProgress(null)
    setPresets([])
    setTasks([])
    setWordbooks([])
    setEntries([])
    setWordbookProgress(null)
    setSelectedWordbookId(null)
    setQuizState(null)
    setSearchResult({ publicHits: [], myHits: [] })
    setSearchSuggestions([])
  }

  async function loadPrivateData(nextPath?: string, authToken = token) {
    if (!authToken) return
    setLoading(true)
    try {
      const [me, system, study, summary, presetData, taskData, wordbookData] = await Promise.all([
        api<AuthUser>('/api/auth/me', undefined, true, authToken),
        api<SystemOverview>('/api/system/overview'),
        api<StudyAgenda>('/api/study/agenda', undefined, true, authToken),
        api<StudyProgress>('/api/study/progress', undefined, true, authToken),
        api<ImportPreset[]>('/api/imports/presets', undefined, true, authToken),
        api<ImportTask[]>('/api/imports/tasks', undefined, true, authToken),
        api<WordbookSummary[]>('/api/wordbooks', undefined, true, authToken),
      ])
      setUser(me)
      setOverview(system)
      setAgenda(study)
      setProgress(summary)
      setPresets(presetData)
      setTasks(taskData)
      setWordbooks(wordbookData)
      setSelectedPlatform(
        presetData.find((preset) => preset.platform === 'ANKI')?.platform ?? DEFAULT_IMPORT_PLATFORM,
      )
      setSelectedWordbookId((current) =>
        current && wordbookData.some((item) => item.id === current) ? current : (wordbookData[0]?.id ?? null),
      )
      if (nextPath) navigate(nextPath)
      else if (!user) navigate(wordbookData.length ? '/library' : '/imports')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const system = await api<SystemOverview>('/api/system/overview')
        if (!alive) return
        setOverview(system)
        if (token) await loadPrivateData()
      } catch (err) {
        if (alive) setError(err instanceof Error ? err.message : '初始化失败')
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    if (!token || !selectedWordbookId) return
    let cancelled = false
    ;(async () => {
      try {
        const [entryData, progressData] = await Promise.all([
          api<VocabularyEntry[]>(`/api/wordbooks/${selectedWordbookId}/entries`, undefined, true),
          api<WordbookProgress>(`/api/wordbooks/${selectedWordbookId}/progress`, undefined, true),
        ])
        if (!cancelled) {
          setEntries(entryData)
          setWordbookProgress(progressData)
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : '加载词书失败')
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
      setSearchResult({ publicHits: [], myHits: [] })
      return
    }

    ;(async () => {
      try {
        const result = await api<WordSearchResponse>(`/api/search/words?q=${encodeURIComponent(query)}`)
        if (!cancelled) setSearchResult(result)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : '搜索失败')
      }
    })()

    return () => {
      cancelled = true
    }
  }, [token, deferredSearchQuery])

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
        const suggestions = await api<SearchSuggestion[]>(
          `/api/search/suggestions?q=${encodeURIComponent(query)}`,
        )
        if (!cancelled) setSearchSuggestions(suggestions)
      } catch (err) {
        if (!cancelled) {
          setSearchSuggestions([])
          setError(err instanceof Error ? err.message : '获取搜索补全失败')
        }
      }
    }, 160)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [token, searchQuery])

  async function handleLogin() {
    setError(null)
    try {
      const result = await api<AuthTokenResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ account, password: loginPassword }),
      })
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      setToken(result.accessToken)
      setUser(result.user)
      await loadPrivateData('/library', result.accessToken)
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
    }
  }

  async function handleRegister() {
    setError(null)
    try {
      const result = await api<AuthTokenResponse>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          username: registerUsername,
          email: registerEmail,
          password: registerPassword,
        }),
      })
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      setToken(result.accessToken)
      setUser(result.user)
      setSourceName(`${result.user.username}-词书`)
      await loadPrivateData('/imports', result.accessToken)
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败')
    }
  }

  async function handleImport() {
    if (!selectedFile) {
      setError('请先选择要导入的文件')
      return
    }
    setError(null)
    setMessage(null)
    try {
      const formData = new FormData()
      formData.append('platform', selectedPlatform)
      if (sourceName.trim()) formData.append('sourceName', sourceName.trim())
      formData.append('file', selectedFile)
      const task = await api<ImportTask>('/api/imports/files', { method: 'POST', body: formData }, true)
      setMessage(`导入完成，新增 ${task.importedCards} 条单词`)
      setSelectedFile(null)
      await loadPrivateData('library')
      if (task.wordbookId) setSelectedWordbookId(task.wordbookId)
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败')
    }
  }

  async function handleCreateQuiz(wordbookId?: number) {
    const targetWordbookId = wordbookId ?? selectedWordbookId
    if (!targetWordbookId) {
      setError('请先选择词书')
      return
    }
    setError(null)
    setMessage(null)
    try {
      const result = await api<QuizSessionState>(
        '/api/quiz/sessions',
        { method: 'POST', body: JSON.stringify({ wordbookId: targetWordbookId, mode: quizMode }) },
        true,
      )
      setQuizState(result)
      navigate('/quiz')
    } catch (err) {
      setError(err instanceof Error ? err.message : '启动斩词失败')
    }
  }

  async function handleAnswer(option: string): Promise<QuizAnswerResult | null> {
    if (!quizState?.currentQuestion) return null
    setError(null)
    try {
      const result = await api<QuizAnswerResult>(
        `/api/quiz/sessions/${quizState.session.id}/answers`,
        {
          method: 'POST',
          body: JSON.stringify({
            attemptId: quizState.currentQuestion.attemptId,
            selectedOption: option,
          }),
        },
        true,
      )
      return result
    } catch (err) {
      setError(err instanceof Error ? err.message : '提交答案失败')
      return null
    }
  }

  function advanceQuiz(result: QuizAnswerResult) {
    setQuizState({ session: result.session, currentQuestion: result.nextQuestion })
    void loadPrivateData()
  }

  async function getWordDetail(entryId: number) {
    return api<WordDetail>(`/api/search/words/${entryId}`)
  }

  function pickSearchSuggestion(value: string) {
    setSearchSuggestions([])
    setSearchQuery(value)
  }

  const preset = presets.find((item) => item.platform === selectedPlatform)
  const selectedWordbook = wordbooks.find((item) => item.id === selectedWordbookId) ?? null

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
    tasks,
    wordbooks,
    entries,
    wordbookProgress,
    selectedWordbookId,
    setSelectedWordbookId,
    searchQuery,
    setSearchQuery,
    searchResult,
    searchSuggestions,
    pickSearchSuggestion,
    quizMode,
    setQuizMode,
    quizState,
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
    handleImport,
    handleCreateQuiz,
    handleAnswer,
    advanceQuiz,
    getWordDetail,
  }
}
