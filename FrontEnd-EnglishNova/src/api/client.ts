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
  const responseText = await response.text()
  let payload: ApiResponse<T>
  try {
    payload = responseText
      ? (JSON.parse(responseText) as ApiResponse<T>)
      : ({
          success: response.ok,
          data: null as T,
          message: response.ok ? '成功' : response.statusText || '请求失败',
          timestamp: new Date().toISOString(),
        } satisfies ApiResponse<T>)
  } catch {
    payload = {
      success: false,
      data: null as T,
      message: response.statusText || `请求失败，状态码 ${response.status}`,
      timestamp: new Date().toISOString(),
    }
  }
  const message = payload.message || '请求失败'
  const unauthorizedByMessage = options?.requireAuth && /token|unauthorized|login/i.test(message)

  if (!response.ok || !payload.success) {
    if (response.status === 401 || unauthorizedByMessage) {
      options?.onUnauthorized?.()
    }
    throw new Error(message)
  }

  return payload.data
}
