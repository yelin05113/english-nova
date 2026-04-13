import { startTransition, useDeferredValue, useEffect, useState } from 'react'
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

  StudyAgenda,
  StudyProgress,
  SystemOverview,
  ViewKey,
  VocabularyEntry,
  WordSearchResponse,
  WordbookProgress,
  WordbookSummary,
} from '../types'

export function useAppState() {
  /* ---- auth ---- */
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) ?? '')
  const [user, setUser] = useState<AuthUser | null>(null)

  /* ---- view routing ---- */
  const [view, setView] = useState<ViewKey>('auth')
  const [authTab, setAuthTab] = useState<'login' | 'register'>('login')

  /* ---- backend data ---- */
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
  const [quizMode, setQuizMode] = useState<QuizMode>('MIXED')
  const [quizState, setQuizState] = useState<QuizSessionState | null>(null)

  /* ---- feedback ---- */
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  /* ---- form fields ---- */
  const [account, setAccount] = useState('demo')
  const [loginPassword, setLoginPassword] = useState('123456')
  const [registerUsername, setRegisterUsername] = useState('')
  const [registerEmail, setRegisterEmail] = useState('')
  const [registerPassword, setRegisterPassword] = useState('')
  const [sourceName, setSourceName] = useState('')
  const [selectedPlatform, setSelectedPlatform] = useState<ImportPlatform>(DEFAULT_IMPORT_PLATFORM)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  /* ---- helpers ---- */
  function api<T>(path: string, init?: RequestInit, requireAuth = false, authToken = token) {
    return apiFetch<T>(path, init, { requireAuth, token: authToken, onUnauthorized: clearAuth })
  }

  function clearAuth() {
    localStorage.removeItem(TOKEN_KEY)
    setToken('')
    setUser(null)
    setView('auth')
    setAgenda(null)
    setProgress(null)
    setPresets([])
    setTasks([])
    setWordbooks([])
    setEntries([])
    setWordbookProgress(null)
    setSelectedWordbookId(null)
    setQuizState(null)
  }

  /* ---- data loading ---- */
  async function loadPrivateData(nextView?: ViewKey, authToken = token) {
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
        current && wordbookData.some((item) => item.id === current)
          ? current
          : (wordbookData[0]?.id ?? null),
      )
      if (nextView) setView(nextView)
      else if (!user) setView(wordbookData.length ? 'library' : 'imports')
    } finally {
      setLoading(false)
    }
  }

  /* ---- effects ---- */
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
    ;(async () => {
      try {
        const [entryData, progressData] = await Promise.all([
          api<VocabularyEntry[]>(`/api/wordbooks/${selectedWordbookId}/entries`, undefined, true),
          api<WordbookProgress>(`/api/wordbooks/${selectedWordbookId}/progress`, undefined, true),
        ])
        setEntries(entryData)
        setWordbookProgress(progressData)
      } catch (err) {
        setError(err instanceof Error ? err.message : '加载词书失败')
      }
    })()
  }, [token, selectedWordbookId])

  useEffect(() => {
    if (!token) return
    ;(async () => {
      try {
        const result = await api<WordSearchResponse>(
          `/api/search/words?q=${encodeURIComponent(deferredSearchQuery.trim())}`,
        )
        setSearchResult(result)
      } catch (err) {
        setError(err instanceof Error ? err.message : '搜索失败')
      }
    })()
  }, [token, deferredSearchQuery])

  /* ---- actions ---- */
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
      await loadPrivateData('library', result.accessToken)
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
      await loadPrivateData('imports', result.accessToken)
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败')
    }
  }

  async function handleImport() {
    if (!selectedFile) return setError('请先选择要导入的文件')
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
    if (!targetWordbookId) return setError('请先选择词书')
    setError(null)
    setMessage(null)
    try {
      const result = await api<QuizSessionState>(
        '/api/quiz/sessions',
        { method: 'POST', body: JSON.stringify({ wordbookId: targetWordbookId, mode: quizMode }) },
        true,
      )
      setQuizState(result)
      setView('quiz')
    } catch (err) {
      setError(err instanceof Error ? err.message : '启动斩词失败')
    }
  }

  async function handleAnswer(option: string) {
    if (!quizState?.currentQuestion) return
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
      setQuizState({ session: result.session, currentQuestion: result.nextQuestion })
      setMessage(
        result.correct
          ? '回答正确，已斩掉这个单词'
          : `回答错误，正确答案是：${result.correctOption}`,
      )
      await loadPrivateData()
    } catch (err) {
      setError(err instanceof Error ? err.message : '提交答案失败')
    }
  }

  /* ---- derived ---- */
  const preset = presets.find((item) => item.platform === selectedPlatform)
  const selectedWordbook = wordbooks.find((item) => item.id === selectedWordbookId) ?? null

  const switchView = (nextView: ViewKey) => startTransition(() => setView(nextView))

  return {
    /* auth */
    token, user, clearAuth,
    /* view routing */
    view, switchView, authTab, setAuthTab,
    /* data */
    overview, agenda, progress, presets, tasks,
    wordbooks, entries, wordbookProgress,
    selectedWordbookId, setSelectedWordbookId,
    searchQuery, setSearchQuery, searchResult,
    quizMode, setQuizMode, quizState,
    /* feedback */
    message, error, loading,
    /* form */
    account, setAccount,
    loginPassword, setLoginPassword,
    registerUsername, setRegisterUsername,
    registerEmail, setRegisterEmail,
    registerPassword, setRegisterPassword,
    sourceName, setSourceName,
    selectedPlatform, setSelectedPlatform,
    selectedFile, setSelectedFile,
    /* derived */
    preset, selectedWordbook,
    /* actions */
    handleLogin, handleRegister, handleImport,
    handleCreateQuiz, handleAnswer,
  }
}
