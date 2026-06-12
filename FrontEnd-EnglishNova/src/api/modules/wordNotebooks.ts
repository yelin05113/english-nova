import { apiFetch, type ApiAuthOptions } from '../client'
import type { SearchEntryType } from './search'

export interface WordNotebookSummary {
  id: number
  name: string
  wordCount: number
  containsWord: boolean
  createdAt: string
}

export interface WordNotebookEntry {
  id: number
  notebookId: number
  normalizedWord: string
  word: string
  phonetic: string
  meaningCn: string
  exampleSentence: string
  correctedExampleSentence: string
  chineseSentence: string
  exampleAudioUrl: string
  sourceEntryType: SearchEntryType | null
  sourceEntryId: number | null
  createdAt: string
}

export interface CreateWordNotebookRequest {
  name: string
}

export interface AddWordNotebookEntryRequest {
  word: string
  phonetic: string
  meaningCn: string
  exampleSentence: string
  correctedExampleSentence: string
  chineseSentence: string
  exampleAudioUrl: string
  optionDetails: Array<{
    value: string
    word: string
    meaningCn: string
  }>
  correctOption: string
  sourceEntryType: SearchEntryType | null
  sourceEntryId: number | null
}

export interface AddWordNotebookEntryResult {
  added: boolean
  notebook: WordNotebookSummary
  entry: WordNotebookEntry
}

function withAuth(options?: ApiAuthOptions) {
  return { requireAuth: true, token: options?.token, onUnauthorized: options?.onUnauthorized }
}

async function listWordNotebooks(options?: ApiAuthOptions, word?: string) {
  const params = new URLSearchParams()
  if (word?.trim()) {
    params.set('word', word.trim())
  }
  const query = params.toString()
  return apiFetch<WordNotebookSummary[]>(`/word-notebooks/list${query ? `?${query}` : ''}`, undefined, withAuth(options))
}

async function createWordNotebook(payload: CreateWordNotebookRequest, options?: ApiAuthOptions) {
  return apiFetch<WordNotebookSummary>(
    '/word-notebooks/create',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    withAuth(options),
  )
}

async function listWordNotebookEntries(notebookId: number, options?: ApiAuthOptions) {
  return apiFetch<WordNotebookEntry[]>(`/word-notebooks/${notebookId}/entries`, undefined, withAuth(options))
}

async function addWordNotebookEntry(
  notebookId: number,
  payload: AddWordNotebookEntryRequest,
  options?: ApiAuthOptions,
) {
  return apiFetch<AddWordNotebookEntryResult>(
    `/word-notebooks/${notebookId}/entries`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    withAuth(options),
  )
}

async function removeWordFromNotebooks(word: string, options?: ApiAuthOptions) {
  const query = new URLSearchParams({ word: word.trim() }).toString()
  return apiFetch<number>(
    `/word-notebooks/entries?${query}`,
    {
      method: 'DELETE',
    },
    withAuth(options),
  )
}

export const wordNotebookApi = {
  listWordNotebooks,
  createWordNotebook,
  listWordNotebookEntries,
  addWordNotebookEntry,
  removeWordFromNotebooks,
}
