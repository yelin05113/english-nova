import { apiFetch, type ApiAuthOptions } from '../client'

export type SearchMatchType = 'EXACT' | 'PREFIX' | 'CONTAINS' | 'TEXT'
export type SearchSuggestionMatchType = Exclude<SearchMatchType, 'TEXT'>
export type SearchEntryType = 'PUBLIC' | 'USER'

export interface SearchHit {
  entryId: number
  entryType: SearchEntryType
  word: string
  phonetic: string
  meaningCn: string
  source: string
  exampleSentence: string
  correctedExampleSentence: string
  chineseSentence: string
  exampleAudioUrl: string
  category: string
  frequencyRank?: number | null
  wordfreqZipf?: number | null
  dataQuality?: string
  visibility: string
  importSource: string
  matchPercent: number
  matchType: SearchMatchType
}

export interface WordSearchResponse {
  hits: SearchHit[]
}

interface LegacyWordSearchResponse {
  publicHits?: SearchHit[]
  myHits?: SearchHit[]
}

export interface SearchSuggestion {
  entryId: number
  entryType: SearchEntryType
  word: string
  visibility: string
  matchPercent: number
  matchType: SearchSuggestionMatchType
}

export interface WordDetail {
  entryId: number
  entryType: SearchEntryType
  ownerUserId: number | null
  wordbookId: number | null
  wordbookName: string
  word: string
  phonetic: string
  meaningCn: string
  exampleSentence: string
  correctedExampleSentence: string
  chineseSentence: string
  exampleAudioUrl: string
  category: string
  bncRank?: number | null
  frqRank?: number | null
  wordfreqZipf?: number | null
  exchangeInfo?: string
  dataQuality?: string
  difficulty: number | null
  visibility: string
  source: string
  sourceName: string
  importSource: string
  audioUrl: string
}

export interface PublicWordbook {
  id: number
  name: string
  sourceName: string
  sourceUrl: string
  licenseName: string
  licenseUrl: string
  wordCount: number
  subscribed: boolean
  completedCount: number
  wrongCount: number
  dailyTargetCount: number
  todayCompletedCount: number
  todayCorrectAttempts: number
  todayTotalAttempts: number
  nextSortOrder: number
  createdAt: string
  updatedAt: string
}

export interface PublicWordbookEntry {
  publicEntryId: number
  sortOrder: number
  word: string
  phonetic: string
  meaningCn: string
  exampleSentence: string
  correctedExampleSentence: string
  chineseSentence: string
  exampleAudioUrl: string
  bncRank: number | null
  frqRank: number | null
  wordfreqZipf: number | null
}

export interface UpdatePublicWordbookDailyTargetRequest {
  dailyTargetCount: number
}

export type EnglishChatRole = 'user' | 'assistant'

export interface EnglishChatMessage {
  role: EnglishChatRole
  content: string
}

export interface EnglishQuestionContext {
  word: string
  meaningCn: string
  exampleSentence: string
  correctedExampleSentence: string
}

export interface EnglishChatRequest {
  messages: EnglishChatMessage[]
  questionContext: EnglishQuestionContext | null
  userPrompt: string
}

export interface EnglishChatStreamEvent {
  type: 'token' | 'done' | 'error'
  text?: string
  message?: string
  reason?: string
}

interface StreamEnglishChatOptions extends ApiAuthOptions {
  signal?: AbortSignal
  onEvent: (event: EnglishChatStreamEvent) => void
}

function normalizeLookupWord(value: string) {
  return value
    .trim()
    .replace(/^[^A-Za-z]+|[^A-Za-z]+$/g, '')
    .toLowerCase()
}

function normalizeWordSearchResponse(
  payload: WordSearchResponse | LegacyWordSearchResponse,
  includeLegacyMyHits = false,
): WordSearchResponse {
  if ('hits' in payload && Array.isArray(payload.hits)) {
    return payload
  }
  const legacyPayload = payload as LegacyWordSearchResponse
  return {
    hits: includeLegacyMyHits
      ? [...(legacyPayload.publicHits ?? []), ...(legacyPayload.myHits ?? [])]
      : (legacyPayload.publicHits ?? []),
  }
}

function withAuth(options?: ApiAuthOptions) {
  return { requireAuth: true, token: options?.token, onUnauthorized: options?.onUnauthorized }
}

function parseStreamPayload(text: string) {
  if (!text) {
    return {}
  }
  try {
    return JSON.parse(text) as Record<string, string>
  } catch {
    return { message: text }
  }
}

function flushSseBuffer(
  buffer: string,
  onEvent: (event: EnglishChatStreamEvent) => void,
) {
  let remaining = buffer
  while (true) {
    const delimiterIndex = remaining.indexOf('\n\n')
    if (delimiterIndex < 0) {
      return remaining
    }

    const block = remaining.slice(0, delimiterIndex)
    remaining = remaining.slice(delimiterIndex + 2)
    if (!block.trim()) {
      continue
    }

    let eventType: EnglishChatStreamEvent['type'] = 'token'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        const maybeType = line.slice(6).trim()
        if (maybeType === 'token' || maybeType === 'done' || maybeType === 'error') {
          eventType = maybeType
        }
        continue
      }
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }

    const payload = parseStreamPayload(dataLines.join('\n'))
    if (eventType === 'token') {
      onEvent({ type: 'token', text: payload.text === 'null' ? '' : (payload.text || '') })
    } else if (eventType === 'error') {
      onEvent({ type: 'error', message: payload.message || 'AI 助手暂时不可用，请稍后再试' })
    } else {
      onEvent({ type: 'done', reason: payload.reason || 'completed' })
    }
  }
}

async function searchWords(query: string, options?: ApiAuthOptions, wordbookId?: number | null) {
  const params = new URLSearchParams({ q: query })
  if (wordbookId != null) {
    params.set('wordbookId', String(wordbookId))
  }
  const response = await apiFetch<WordSearchResponse | LegacyWordSearchResponse>(
    `/search/words?${params.toString()}`,
    undefined,
    withAuth(options),
  )
  return normalizeWordSearchResponse(response, wordbookId != null)
}

async function searchSuggestions(query: string, options?: ApiAuthOptions, wordbookId?: number | null) {
  const params = new URLSearchParams({ q: query })
  if (wordbookId != null) {
    params.set('wordbookId', String(wordbookId))
  }
  const suggestions = await apiFetch<SearchSuggestion[]>(
    `/search/suggestions?${params.toString()}`,
    undefined,
    withAuth(options),
  )
  return wordbookId == null
    ? suggestions.filter((suggestion) => suggestion.visibility === 'PUBLIC')
    : suggestions
}

async function getWordDetail(entryId: number, options?: ApiAuthOptions) {
  return getWordDetailByType(entryId, 'PUBLIC', options)
}

async function getWordDetailByType(entryId: number, entryType: SearchEntryType, options?: ApiAuthOptions) {
  const params = new URLSearchParams({ entryType })
  return apiFetch<WordDetail>(`/search/words/${entryId}?${params.toString()}`, undefined, withAuth(options))
}

async function findWordDetailByWord(
  word: string,
  options?: ApiAuthOptions,
  preferredEntryType?: SearchEntryType,
) {
  const normalizedWord = normalizeLookupWord(word)
  if (!normalizedWord) {
    return null
  }

  const result = await searchWords(normalizedWord, options)
  const exactHits = result.hits.filter((hit) => normalizeLookupWord(hit.word) === normalizedWord)
  if (exactHits.length === 0) {
    return null
  }

  const orderedHits = preferredEntryType
    ? exactHits.slice().sort((left, right) => {
        if (left.entryType === preferredEntryType && right.entryType !== preferredEntryType) return -1
        if (left.entryType !== preferredEntryType && right.entryType === preferredEntryType) return 1
        return 0
      })
    : exactHits

  const target = orderedHits[0]
  return getWordDetailByType(target.entryId, target.entryType, options)
}

async function listPublicWordbooks(options?: ApiAuthOptions) {
  return apiFetch<PublicWordbook[]>('/public-wordbooks', undefined, withAuth(options))
}

async function listPublicWordbookEntries(publicWordbookId: number, options?: ApiAuthOptions) {
  return apiFetch<PublicWordbookEntry[]>(
    `/public-wordbooks/${publicWordbookId}/entries`,
    undefined,
    withAuth(options),
  )
}

async function subscribePublicWordbook(publicWordbookId: number, options?: ApiAuthOptions) {
  return apiFetch<PublicWordbook>(
    `/public-wordbooks/${publicWordbookId}/subscribe`,
    { method: 'POST' },
    withAuth(options),
  )
}

async function unsubscribePublicWordbook(publicWordbookId: number, options?: ApiAuthOptions) {
  return apiFetch<PublicWordbook>(
    `/public-wordbooks/${publicWordbookId}/unsubscribe`,
    { method: 'POST' },
    withAuth(options),
  )
}

async function resetPublicWordbookProgress(publicWordbookId: number, options?: ApiAuthOptions) {
  return apiFetch<PublicWordbook>(
    `/public-wordbooks/${publicWordbookId}/reset-progress`,
    { method: 'POST' },
    withAuth(options),
  )
}

async function updatePublicWordbookDailyTarget(
  publicWordbookId: number,
  payload: UpdatePublicWordbookDailyTargetRequest,
  options?: ApiAuthOptions,
) {
  return apiFetch<PublicWordbook>(
    `/public-wordbooks/${publicWordbookId}/daily-target`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    withAuth(options),
  )
}

async function streamEnglishChat(payload: EnglishChatRequest, options: StreamEnglishChatOptions) {
  const headers = new Headers()
  headers.set('Content-Type', 'application/json')
  headers.set('Accept', 'text/event-stream')
  if (options.token) {
    headers.set('Authorization', `Bearer ${options.token}`)
  }

  const response = await fetch('/search/ai/english-chat', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
    signal: options.signal,
  })

  if (response.status === 401) {
    options.onUnauthorized?.()
  }

  if (!response.ok) {
    const responseText = await response.text()
    const parsed = parseStreamPayload(responseText)
    throw new Error(parsed.message || response.statusText || 'AI 助手暂时不可用，请稍后再试')
  }

  if (!response.body) {
    throw new Error('AI 助手没有返回可读取的数据流')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      buffer = flushSseBuffer(buffer, options.onEvent)
      break
    }
    buffer += decoder.decode(value, { stream: true })
    buffer = flushSseBuffer(buffer, options.onEvent)
  }
}

export const searchApi = {
  searchWords,
  searchSuggestions,
  getWordDetail,
  getWordDetailByType,
  findWordDetailByWord,
  listPublicWordbooks,
  listPublicWordbookEntries,
  subscribePublicWordbook,
  unsubscribePublicWordbook,
  resetPublicWordbookProgress,
  updatePublicWordbookDailyTarget,
  streamEnglishChat,
}
