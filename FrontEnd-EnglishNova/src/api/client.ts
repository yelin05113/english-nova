export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

export interface ApiAuthOptions {
  token?: string
  onUnauthorized?: () => void
}

interface ApiRequestOptions extends ApiAuthOptions {
  requireAuth?: boolean
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
  options?: ApiRequestOptions,
): Promise<T> {
  const headers = new Headers(init?.headers)
  if (!(init?.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (options?.token) {
    headers.set('Authorization', `Bearer ${options.token}`)
  }

  const response = await fetch(path, { ...init, headers })
  const payload = (await response.json()) as ApiResponse<T>
  const message = payload.message || 'Request failed'
  const unauthorizedByMessage = options?.requireAuth && /token|unauthorized|login/i.test(message)

  if (!response.ok || !payload.success) {
    if (response.status === 401 || unauthorizedByMessage) {
      options?.onUnauthorized?.()
    }
    throw new Error(message)
  }

  return payload.data
}
