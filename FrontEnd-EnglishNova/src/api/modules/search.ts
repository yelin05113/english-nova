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
  tag: string
  wordCount: number
  subscribed: boolean
  completedCount: number
  wrongCount: number
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
  bncRank: number | null
  frqRank: number | null
  wordfreqZipf: number | null
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

export const searchApi = {
  searchWords,
  searchSuggestions,
  getWordDetail,
  getWordDetailByType,
  listPublicWordbooks,
  listPublicWordbookEntries,
  subscribePublicWordbook,
  unsubscribePublicWordbook,
  resetPublicWordbookProgress,
}
