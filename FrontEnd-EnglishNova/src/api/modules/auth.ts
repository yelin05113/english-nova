import { apiFetch, type ApiAuthOptions } from '../client'
import type { QuizOptionStrategy } from './quiz'

export interface AuthUser {
  id: number
  username: string
  avatarUrl: string | null
  quizOptionStrategy: QuizOptionStrategy
}

export interface AuthTokenResponse {
  accessToken: string
  user: AuthUser
}

export interface LoginRequest {
  account: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface UpdateProfileRequest {
  username: string
  avatarUrl: string | null
}

export interface UpdateQuizOptionStrategyRequest {
  quizOptionStrategy: QuizOptionStrategy
}

function withAuth(options?: ApiAuthOptions) {
  return { requireAuth: true, token: options?.token, onUnauthorized: options?.onUnauthorized }
}

async function login(payload: LoginRequest) {
  return apiFetch<AuthTokenResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

async function register(payload: RegisterRequest) {
  return apiFetch<AuthTokenResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

async function me(options?: ApiAuthOptions) {
  return apiFetch<AuthUser>('/auth/me', undefined, withAuth(options))
}

async function updateProfile(payload: UpdateProfileRequest, options?: ApiAuthOptions) {
  return apiFetch<AuthTokenResponse>(
    '/auth/profile',
    {
      method: 'PATCH',
      body: JSON.stringify(payload),
    },
    withAuth(options),
  )
}

async function updateQuizOptionStrategy(payload: UpdateQuizOptionStrategyRequest, options?: ApiAuthOptions) {
  return apiFetch<AuthUser>(
    '/auth/preferences/quiz-option-strategy',
    {
      method: 'PATCH',
      body: JSON.stringify(payload),
    },
    withAuth(options),
  )
}

async function uploadAvatar(file: File, options?: ApiAuthOptions) {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch<AuthTokenResponse>(
    '/auth/profile/avatar',
    {
      method: 'POST',
      body: formData,
    },
    withAuth(options),
  )
}

export const authApi = {
  login,
  register,
  me,
  updateProfile,
  updateQuizOptionStrategy,
  uploadAvatar,
}
