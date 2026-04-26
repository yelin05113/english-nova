import { useEffect } from 'react'
import './App.css'
import { useAppState } from './hooks/useAppState'
import { AppStateContext } from './context/AppStateContext'
import { AppRouter } from './router'

function App() {
  const state = useAppState()

  useEffect(() => {
    document.body.classList.toggle('layout-default', state.layoutMode === 'default')
    document.body.classList.toggle('layout-pixel', state.layoutMode === 'pixel')
    return () => {
      document.body.classList.remove('layout-default', 'layout-pixel')
    }
  }, [state.layoutMode])

  return (
    <AppStateContext.Provider value={state}>
      <AppRouter />
    </AppStateContext.Provider>
  )
}

export default App

