import { apiFetch, type ApiAuthOptions } from '../client'

export interface AuthUser {
  id: number
  username: string
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

export const authApi = {
  login,
  register,
  me,
}
