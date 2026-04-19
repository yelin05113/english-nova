import { useDeferredValue, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import type { ApiAuthOptions } from '../api/client'
import { authApi, type AuthUser } from '../api/modules/auth'
import { importApi, type ImportPlatform, type ImportPreset, type ImportTask } from '../api/modules/imports'
import {
  quizApi,
  type QuizAnswerResult,
  type QuizMode,
  type QuizSessionState,
  type VocabularyEntry,
  type WordbookProgress,
  type WordbookSummary,
} from '../api/modules/quiz'
import { searchApi, type SearchSuggestion, type WordSearchResponse } from '../api/modules/search'
import { studyApi, type StudyAgenda, type StudyProgress } from '../api/modules/study'
import { systemApi, type SystemOverview } from '../api/modules/system'
import { DEFAULT_IMPORT_PLATFORM, TOKEN_KEY } from '../constants'

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
  const [searchResult, setSearchResult] = useState<WordSearchResponse>({ hits: [] })
  const [searchSuggestions, setSearchSuggestions] = useState<SearchSuggestion[]>([])
  const [librarySearchQuery, setLibrarySearchQuery] = useState('')
  const deferredLibrarySearchQuery = useDeferredValue(librarySearchQuery)
  const [librarySearchResult, setLibrarySearchResult] = useState<WordSearchResponse>({ hits: [] })
  const [librarySearchSuggestions, setLibrarySearchSuggestions] = useState<SearchSuggestion[]>([])
  const [quizMode, setQuizMode] = useState<QuizMode>('MIXED')
  const [quizState, setQuizState] = useState<QuizSessionState | null>(null)

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
      const [me, system, study, summary, presetData, taskData, wordbookData] = await Promise.all([
        authApi.me(options),
        systemApi.getOverview(),
        studyApi.getAgenda(options),
        studyApi.getProgress(options),
        importApi.listPresets(options),
        importApi.listTasks(options),
        quizApi.listWordbooks(options),
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
      if (nextPath) {
        navigate(nextPath)
      } else if (!user) {
        navigate(wordbookData.length ? '/library' : '/imports')
      }
    } finally {
      setLoading(false)
    }
  }

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
          setError(err instanceof Error ? err.message : 'Failed to initialize the app')
        }
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
          setError(err instanceof Error ? err.message : 'Failed to load the wordbook')
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
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Search failed')
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
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Wordbook search failed')
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
        }
      } catch (err) {
        if (!cancelled) {
          setSearchSuggestions([])
          setError(err instanceof Error ? err.message : 'Failed to load suggestions')
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
        }
      } catch (err) {
        if (!cancelled) {
          setLibrarySearchSuggestions([])
          setError(err instanceof Error ? err.message : 'Failed to load wordbook suggestions')
        }
      }
    }, 160)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [token, selectedWordbookId, librarySearchQuery])

  async function handleLogin() {
    setError(null)
    try {
      const result = await authApi.login({ account, password: loginPassword })
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      setToken(result.accessToken)
      setUser(result.user)
      await loadPrivateData('/library', result.accessToken)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    }
  }

  async function handleRegister() {
    setError(null)
    try {
      const result = await authApi.register({
        username: registerUsername,
        email: registerEmail,
        password: registerPassword,
      })
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      setToken(result.accessToken)
      setUser(result.user)
      setSourceName(`${result.user.username}-wordbook`)
      await loadPrivateData('/imports', result.accessToken)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Register failed')
    }
  }

  async function handleImport() {
    if (!selectedFile) {
      setError('Please select a file first')
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
      setMessage(`Import finished. Added ${task.importedCards} entries.`)
      setSelectedFile(null)
      await loadPrivateData('/library')
      if (task.wordbookId) {
        setSelectedWordbookId(task.wordbookId)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Import failed')
    }
  }

  async function handleCreateQuiz(wordbookId?: number) {
    const targetWordbookId = wordbookId ?? selectedWordbookId
    if (!targetWordbookId) {
      setError('Please select a wordbook first')
      return
    }
    setError(null)
    setMessage(null)
    try {
      const result = await quizApi.createSession(
        { wordbookId: targetWordbookId, mode: quizMode },
        authOptions(),
      )
      setQuizState(result)
      navigate('/quiz')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to start quiz')
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
      setError(err instanceof Error ? err.message : 'Failed to submit answer')
      return null
    }
  }

  function advanceQuiz(result: QuizAnswerResult) {
    setQuizState({ session: result.session, currentQuestion: result.nextQuestion })
    void loadPrivateData()
  }

  async function getWordDetail(entryId: number) {
    return searchApi.getWordDetail(entryId, authOptions())
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
    librarySearchQuery,
    setLibrarySearchQuery,
    librarySearchResult,
    librarySearchSuggestions,
    pickLibrarySearchSuggestion,
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
