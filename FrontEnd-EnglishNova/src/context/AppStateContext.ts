import { createContext, use } from 'react'
import type { useAppState } from '../hooks/useAppState'

export type AppState = ReturnType<typeof useAppState>

export const AppStateContext = createContext<AppState | null>(null)

export function useAppStateContext(): AppState {
  const ctx = use(AppStateContext)
  if (!ctx) throw new Error('useAppStateContext must be used within AppStateContext.Provider')
  return ctx
}
