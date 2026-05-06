import type { ImportPlatform } from './api/modules/imports'

export const TOKEN_KEY = 'english-nova.jwt'
export const AUTH_IDLE_TIMEOUT_MS = 30 * 60 * 1000
export const DEFAULT_IMPORT_PLATFORM: ImportPlatform = 'ANKI'

export const navItems: Array<{ path: string; label: string; requiresAuth?: boolean }> = [
  { path: '/library', label: '词书概览' },
  { path: '/quiz', label: '背词', requiresAuth: true },
  { path: '/notebooks', label: '单词本', requiresAuth: true },
  { path: '/imports', label: '导入', requiresAuth: true },
  { path: '/progress', label: '进度', requiresAuth: true },
  { path: '/search', label: '全局搜索' },
]
