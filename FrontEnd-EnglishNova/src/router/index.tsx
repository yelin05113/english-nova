import { Navigate, Route, Routes } from 'react-router'
import { ImportsView } from '../components/ImportsView'
import { LibraryView } from '../components/LibraryView'
import { ProgressView } from '../components/ProgressView'
import { QuizView } from '../components/QuizView'
import { SearchView } from '../components/SearchView'
import { WordNotebooksView } from '../components/WordNotebooksView'
import { AppLayout } from '../layouts/AppLayout'
import { HomeView } from '../views/HomeView'
import { ProfileView } from '../views/ProfileView'
import { ProtectedGuard } from './guards'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomeView />} />
        <Route path="library" element={<LibraryView />} />
        <Route path="quiz" element={<QuizView />} />
        <Route path="search" element={<SearchView />} />

        <Route element={<ProtectedGuard />}>
          <Route path="imports" element={<ImportsView />} />
          <Route path="notebooks" element={<WordNotebooksView />} />
          <Route path="progress" element={<ProgressView />} />
          <Route path="profile" element={<ProfileView />} />
        </Route>
      </Route>

      <Route path="/auth" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
