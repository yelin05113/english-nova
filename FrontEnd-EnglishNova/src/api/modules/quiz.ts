import { apiFetch, type ApiAuthOptions } from '../client'
import type { ImportPlatform } from './imports'

export type QuizMode = 'CN_TO_EN' | 'EN_TO_CN' | 'MIXED'
export type PromptType = 'CN_TO_EN' | 'EN_TO_CN'
export type QuizTargetType = 'USER_WORDBOOK' | 'PUBLIC_WORDBOOK'

export interface WordbookSummary {
  id: number
  name: string
  platform: ImportPlatform
  wordCount: number
  clearedCount: number
  pendingCount: number
  createdAt: string
}

export interface VocabularyEntry {
  id: number
  word: string
  phonetic: string
  meaningCn: string
  exampleSentence: string
  category: string
  difficulty: number
  visibility: string
}

export interface WordbookProgress {
  wordbookId: number
  wordCount: number
  clearedCount: number
  inProgressCount: number
  pendingCount: number
}

export interface QuizQuestion {
  attemptId: number
  promptType: PromptType
  promptText: string
  currentWord: string
  phonetic: string
  audioUrl: string
  options: string[]
  progress: number
  totalQuestions: number
}

export interface QuizSession {
  id: string
  wordbookId: number
  targetType: QuizTargetType
  targetId: number
  mode: QuizMode
  totalQuestions: number
  answeredQuestions: number
  correctAnswers: number
  todayCorrectAttempts: number
  todayTotalAttempts: number
  status: string
}

export interface QuizSessionState {
  session: QuizSession
  currentQuestion: QuizQuestion | null
}

export interface PublicWordbookProgressSnapshot {
  publicWordbookId: number
  completedCount: number
  dailyTargetCount: number
  todayCompletedCount: number
  wordCount: number
}

export interface QuizAnswerResult {
  correct: boolean
  correctOption: string
  remainingQuestions: number
  dailyTargetJustCompleted: boolean
  publicWordbookProgress: PublicWordbookProgressSnapshot | null
  session: QuizSession
  nextQuestion: QuizQuestion | null
}

export interface CreateQuizSessionRequest {
  targetType: QuizTargetType
  targetId: number
  mode: QuizMode
}

export interface AnswerQuizQuestionRequest {
  attemptId: number
  selectedOption: string
}

function withAuth(options?: ApiAuthOptions) {
  return { requireAuth: true, token: options?.token, onUnauthorized: options?.onUnauthorized }
}

async function listWordbooks(options?: ApiAuthOptions) {
  return apiFetch<WordbookSummary[]>('/wordbooks', undefined, withAuth(options))
}

async function listWordbookEntries(wordbookId: number, options?: ApiAuthOptions) {
  return apiFetch<VocabularyEntry[]>(`/wordbooks/${wordbookId}/entries`, undefined, withAuth(options))
}

async function getWordbookProgress(wordbookId: number, options?: ApiAuthOptions) {
  return apiFetch<WordbookProgress>(`/wordbooks/${wordbookId}/progress`, undefined, withAuth(options))
}

async function createSession(payload: CreateQuizSessionRequest, options?: ApiAuthOptions) {
  return apiFetch<QuizSessionState>(
    '/quiz/sessions',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    withAuth(options),
  )
}

async function getSessionState(sessionId: string, options?: ApiAuthOptions) {
  return apiFetch<QuizSessionState>(`/quiz/sessions/${sessionId}`, undefined, withAuth(options))
}

async function answerQuestion(
  sessionId: string,
  payload: AnswerQuizQuestionRequest,
  options?: ApiAuthOptions,
) {
  return apiFetch<QuizAnswerResult>(
    `/quiz/sessions/${sessionId}/answers`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    withAuth(options),
  )
}

export const quizApi = {
  listWordbooks,
  listWordbookEntries,
  getWordbookProgress,
  createSession,
  getSessionState,
  answerQuestion,
}
