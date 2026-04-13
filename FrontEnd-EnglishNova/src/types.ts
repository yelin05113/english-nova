export type ViewKey = 'auth' | 'library' | 'imports' | 'quiz' | 'search' | 'progress'

export type ImportPlatform = 'BAICIZHAN' | 'BUBEIDANCI' | 'SHANBAY' | 'ANKI'
export type QuizMode = 'CN_TO_EN' | 'EN_TO_CN' | 'MIXED'
export type PromptType = 'CN_TO_EN' | 'EN_TO_CN'

export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

export interface SystemModule {
  name: string
  responsibility: string
  status: string
}

export interface SystemOverview {
  productName: string
  theme: string
  supportedPlatforms: string[]
  modules: SystemModule[]
  deliveryPhases: string[]
}

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

export interface AuthUser {
  id: number
  username: string
}

export interface AuthTokenResponse {
  accessToken: string
  user: AuthUser
}

export interface ImportPreset {
  platform: ImportPlatform
  title: string
  description: string
  acceptedExtensions: string[]
  mappedFields: string[]
}

export interface ImportTask {
  taskId: string
  wordbookId: number | null
  platform: ImportPlatform
  sourceName: string
  estimatedCards: number
  importedCards: number
  status: string
  queuedAt: string
  finishedAt: string | null
  queueName: string
}

export interface SearchHit {
  entryId: number
  word: string
  phonetic: string
  meaningCn: string
  source: string
  exampleSentence: string
  category: string
  visibility: string
}

export interface WordSearchResponse {
  publicHits: SearchHit[]
  myHits: SearchHit[]
}

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
  options: string[]
  progress: number
  totalQuestions: number
}

export interface QuizSession {
  id: string
  wordbookId: number
  mode: QuizMode
  totalQuestions: number
  answeredQuestions: number
  correctAnswers: number
  status: string
}

export interface QuizSessionState {
  session: QuizSession
  currentQuestion: QuizQuestion | null
}

export interface QuizAnswerResult {
  correct: boolean
  correctOption: string
  remainingQuestions: number
  session: QuizSession
  nextQuestion: QuizQuestion | null
}
