import './App.css'
import { useAppState } from './hooks/useAppState'
import { AppStateContext } from './context/AppStateContext'
import { AppRouter } from './router'

function App() {
  const state = useAppState()

  return (
    <AppStateContext.Provider value={state}>
      <AppRouter />
    </AppStateContext.Provider>
  )
}

export default App

