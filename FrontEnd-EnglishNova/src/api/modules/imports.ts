import { apiFetch, type ApiAuthOptions } from '../client'

export type ImportPlatform = 'BAICIZHAN' | 'BUBEIDANCI' | 'SHANBAY' | 'ANKI'

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

export interface ImportFileRequest {
  platform: ImportPlatform
  sourceName?: string
  file: File
}

function withAuth(options?: ApiAuthOptions) {
  return { requireAuth: true, token: options?.token, onUnauthorized: options?.onUnauthorized }
}

async function listPresets(options?: ApiAuthOptions) {
  return apiFetch<ImportPreset[]>('/api/imports/presets', undefined, withAuth(options))
}

async function listTasks(options?: ApiAuthOptions) {
  return apiFetch<ImportTask[]>('/api/imports/tasks', undefined, withAuth(options))
}

async function importFile(payload: ImportFileRequest, options?: ApiAuthOptions) {
  const formData = new FormData()
  formData.append('platform', payload.platform)
  if (payload.sourceName?.trim()) {
    formData.append('sourceName', payload.sourceName.trim())
  }
  formData.append('file', payload.file)

  return apiFetch<ImportTask>('/api/imports/files', { method: 'POST', body: formData }, withAuth(options))
}

export const importApi = {
  listPresets,
  listTasks,
  importFile,
}
