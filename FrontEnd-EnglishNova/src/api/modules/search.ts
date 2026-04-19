import { apiFetch, type ApiAuthOptions } from '../client'

export type SearchMatchType = 'EXACT' | 'PREFIX' | 'CONTAINS' | 'TEXT'
export type SearchSuggestionMatchType = Exclude<SearchMatchType, 'TEXT'>

export interface SearchHit {
  entryId: number
  word: string
  phonetic: string
  meaningCn: string
  source: string
  exampleSentence: string
  category: string
  definitionEn?: string
  tags?: string
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
  word: string
  visibility: string
  matchPercent: number
  matchType: SearchSuggestionMatchType
}

export interface WordDetail {
  entryId: number
  ownerUserId: number | null
  wordbookId: number
  wordbookName: string
  word: string
  phonetic: string
  meaningCn: string
  exampleSentence: string
  category: string
  definitionEn?: string
  tags?: string
  bncRank?: number | null
  frqRank?: number | null
  wordfreqZipf?: number | null
  exchangeInfo?: string
  dataQuality?: string
  difficulty: number
  visibility: string
  source: string
  sourceName: string
  importSource: string
  audioUrl: string
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
    `/api/search/words?${params.toString()}`,
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
    `/api/search/suggestions?${params.toString()}`,
    undefined,
    withAuth(options),
  )
  return wordbookId == null
    ? suggestions.filter((suggestion) => suggestion.visibility === 'PUBLIC')
    : suggestions
}

async function getWordDetail(entryId: number, options?: ApiAuthOptions) {
  return apiFetch<WordDetail>(`/api/search/words/${entryId}`, undefined, withAuth(options))
}

export const searchApi = {
  searchWords,
  searchSuggestions,
  getWordDetail,
}
