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
  visibility: string
  importSource: string
  matchPercent: number
  matchType: SearchMatchType
}

export interface WordSearchResponse {
  publicHits: SearchHit[]
  myHits: SearchHit[]
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
  difficulty: number
  visibility: string
  source: string
  sourceName: string
  importSource: string
  audioUrl: string
}

function withAuth(options?: ApiAuthOptions) {
  return { requireAuth: true, token: options?.token, onUnauthorized: options?.onUnauthorized }
}

async function searchWords(query: string, options?: ApiAuthOptions) {
  return apiFetch<WordSearchResponse>(
    `/api/search/words?q=${encodeURIComponent(query)}`,
    undefined,
    withAuth(options),
  )
}

async function searchSuggestions(query: string, options?: ApiAuthOptions) {
  return apiFetch<SearchSuggestion[]>(
    `/api/search/suggestions?q=${encodeURIComponent(query)}`,
    undefined,
    withAuth(options),
  )
}

async function getWordDetail(entryId: number, options?: ApiAuthOptions) {
  return apiFetch<WordDetail>(`/api/search/words/${entryId}`, undefined, withAuth(options))
}

export const searchApi = {
  searchWords,
  searchSuggestions,
  getWordDetail,
}
