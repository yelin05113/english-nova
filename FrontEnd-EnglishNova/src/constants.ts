import type { ImportPlatform, ViewKey } from './types'

export const TOKEN_KEY = 'english-nova.jwt'
export const DEFAULT_IMPORT_PLATFORM: ImportPlatform = 'ANKI'

export const navItems: Array<{ key: ViewKey; label: string }> = [
  { key: 'library', label: '我的词书' },
  { key: 'imports', label: '导入中心' },
  { key: 'quiz', label: '单词斩杀' },
  { key: 'search', label: '全局搜索' },
  { key: 'progress', label: '我的进度' },
]
