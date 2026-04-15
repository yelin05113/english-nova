import './App.css'
import { Route, Routes } from 'react-router'
import { useAppState } from './hooks/useAppState'
import { AppStateContext } from './context/AppStateContext'
import { AppLayout } from './layouts/AppLayout'
import { HomeView } from './views/HomeView'
import { AuthView } from './views/AuthView'
import { LibraryView } from './components/LibraryView'
import { ImportsView } from './components/ImportsView'
import { QuizView } from './components/QuizView'
import { SearchView } from './components/SearchView'
import { ProgressView } from './components/ProgressView'

function App() {
  const state = useAppState()

  return (
    <AppStateContext.Provider value={state}>
      <Routes>
        <Route path="/auth" element={<AuthView />} />
        <Route element={<AppLayout />}>
          <Route index element={<HomeView />} />
          <Route path="library" element={<LibraryView />} />
          <Route path="quiz" element={<QuizView />} />
          <Route path="imports" element={<ImportsView />} />
          <Route path="progress" element={<ProgressView />} />
          <Route path="search" element={<SearchView />} />
        </Route>
      </Routes>
    </AppStateContext.Provider>
  )
}

export default App

