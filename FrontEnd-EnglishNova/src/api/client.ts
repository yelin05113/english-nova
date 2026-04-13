import type { ApiResponse } from '../types'

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
  options?: { requireAuth?: boolean; token?: string; onUnauthorized?: () => void },
): Promise<T> {
  const headers = new Headers(init?.headers)
  if (!(init?.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (options?.token) headers.set('Authorization', `Bearer ${options.token}`)

  const response = await fetch(path, { ...init, headers })
  const payload = (await response.json()) as ApiResponse<T>

  if (!response.ok || !payload.success) {
    if (
      response.status === 401 ||
      (options?.requireAuth && /登录|令牌/.test(payload.message || ''))
    ) {
      options?.onUnauthorized?.()
    }
    throw new Error(payload.message || '请求失败')
  }

  return payload.data
}
