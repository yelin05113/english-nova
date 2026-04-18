import { apiFetch, type ApiAuthOptions } from '../client'

export interface StudyAgenda {
  newCards: number
  reviewCards: number
  listeningCards: number
  estimatedMinutes: number
  focusAreas: string[]
}

export interface StudyProgress {
  totalWords: number
  clearedWords: number
  inProgressWords: number
  newWords: number
  wordbooks: number
  answeredQuestions: number
  correctAnswers: number
  accuracyRate: number
}

function withAuth(options?: ApiAuthOptions) {
  return { requireAuth: true, token: options?.token, onUnauthorized: options?.onUnauthorized }
}

async function getAgenda(options?: ApiAuthOptions) {
  return apiFetch<StudyAgenda>('/api/study/agenda', undefined, withAuth(options))
}

async function getProgress(options?: ApiAuthOptions) {
  return apiFetch<StudyProgress>('/api/study/progress', undefined, withAuth(options))
}

export const studyApi = {
  getAgenda,
  getProgress,
}
