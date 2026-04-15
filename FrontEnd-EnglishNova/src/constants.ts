import type { ImportPlatform } from './types/types'

export const TOKEN_KEY = 'english-nova.jwt'
export const DEFAULT_IMPORT_PLATFORM: ImportPlatform = 'ANKI'

export const navItems: Array<{ path: string; label: string }> = [
  { path: '/', label: '主页' },
  { path: '/library', label: '词书概览' },
  { path: '/quiz', label: '背词' },
  { path: '/imports', label: '导入' },
  { path: '/progress', label: '进度' },
  { path: '/search', label: '全局搜索' },
]
