import './App.css'
import { navItems } from './constants'
import { useAppState } from './hooks/useAppState'
import { Topbar } from './components/Topbar'
import { HeroPanel } from './components/HeroPanel'
import { AuthPanel } from './components/AuthPanel'
import { NavSidebar } from './components/NavSidebar'
import { LibraryView } from './components/LibraryView'
import { ImportsView } from './components/ImportsView'
import { QuizView } from './components/QuizView'
import { SearchView } from './components/SearchView'
import { ProgressView } from './components/ProgressView'

function App() {
  const state = useAppState()

  return (
    <div className="app-shell">
      <Topbar user={state.user} onLogout={state.clearAuth} />

      <HeroPanel
        wordbooks={state.wordbooks}
        agenda={state.agenda}
        searchResult={state.searchResult}
      />

      {state.error && <p className="notice error">{state.error}</p>}
      {state.message && <p className="notice success">{state.message}</p>}

      {!state.user ? (
        <AuthPanel
          authTab={state.authTab}
          setAuthTab={state.setAuthTab}
          account={state.account}
          setAccount={state.setAccount}
          loginPassword={state.loginPassword}
          setLoginPassword={state.setLoginPassword}
          registerUsername={state.registerUsername}
          setRegisterUsername={state.setRegisterUsername}
          registerEmail={state.registerEmail}
          setRegisterEmail={state.setRegisterEmail}
          registerPassword={state.registerPassword}
          setRegisterPassword={state.setRegisterPassword}
          overview={state.overview}
          onLogin={state.handleLogin}
          onRegister={state.handleRegister}
        />
      ) : (
        <section className="workspace">
          <NavSidebar view={state.view} onSwitch={state.switchView} />

          <article className="panel content">
            <div className="panel-head">
              <h3>{navItems.find((item) => item.key === state.view)?.label ?? '工作区'}</h3>
              {state.loading && <span className="badge">同步中</span>}
            </div>

            {state.view === 'library' && (
              <LibraryView
                wordbooks={state.wordbooks}
                selectedWordbookId={state.selectedWordbookId}
                onSelectWordbook={state.setSelectedWordbookId}
                selectedWordbook={state.selectedWordbook}
                wordbookProgress={state.wordbookProgress}
                entries={state.entries}
                onStartQuiz={() => void state.handleCreateQuiz()}
              />
            )}

            {state.view === 'imports' && (
              <ImportsView
                presets={state.presets}
                selectedPlatform={state.selectedPlatform}
                onPlatformChange={state.setSelectedPlatform}
                sourceName={state.sourceName}
                onSourceNameChange={state.setSourceName}
                preset={state.preset}
                onFileChange={state.setSelectedFile}
                tasks={state.tasks}
                onImport={state.handleImport}
              />
            )}

            {state.view === 'quiz' && (
              <QuizView
                quizMode={state.quizMode}
                onQuizModeChange={state.setQuizMode}
                quizState={state.quizState}
                onCreateQuiz={() => void state.handleCreateQuiz()}
                onAnswer={state.handleAnswer}
              />
            )}

            {state.view === 'search' && (
              <SearchView
                searchQuery={state.searchQuery}
                onSearchQueryChange={state.setSearchQuery}
                searchResult={state.searchResult}
              />
            )}

            {state.view === 'progress' && (
              <ProgressView progress={state.progress} agenda={state.agenda} />
            )}
          </article>
        </section>
      )}
    </div>
  )
}

export default App
