import { Route, Routes } from 'react-router'
import { ImportsView } from '../components/ImportsView'
import { LibraryView } from '../components/LibraryView'
import { ProgressView } from '../components/ProgressView'
import { QuizView } from '../components/QuizView'
import { SearchView } from '../components/SearchView'
import { AppLayout } from '../layouts/AppLayout'
import { AuthView } from '../views/AuthView'
import { HomeView } from '../views/HomeView'
import { AuthGuard, ProtectedGuard } from './guards'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AuthGuard />}>
        <Route path="/auth" element={<AuthView />} />
      </Route>

      <Route element={<ProtectedGuard />}>
        <Route element={<AppLayout />}>
          <Route index element={<HomeView />} />
          <Route path="library" element={<LibraryView />} />
          <Route path="quiz" element={<QuizView />} />
          <Route path="imports" element={<ImportsView />} />
          <Route path="progress" element={<ProgressView />} />
          <Route path="search" element={<SearchView />} />
        </Route>
      </Route>
    </Routes>
  )
}
