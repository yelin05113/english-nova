import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react'
import naruto1 from '../assets/naruto-1.png'
import naruto2 from '../assets/naruto-2.png'
import naruto3 from '../assets/naruto-3.png'
import naruto4 from '../assets/naruto-4.png'
import type { QuizQuestion } from '../api/modules/quiz'
import {
  searchApi,
  type EnglishChatMessage,
  type EnglishChatStreamEvent,
  type EnglishQuestionContext,
} from '../api/modules/search'

interface QuizAiAssistantProps {
  token: string
  question: QuizQuestion | null
  onUnauthorized?: () => void
}

const AI_ASSISTANT_IMAGES = [naruto1, naruto2, naruto3, naruto4]

function buildQuestionContext(question: QuizQuestion | null): EnglishQuestionContext | null {
  if (!question) {
    return null
  }

  const word = (question.currentWord || question.promptText || '').trim()
  const meaningCn = (question.meaningCn || '').trim()
  const correctedExampleSentence = (question.correctedExampleSentence || '').trim()
  const exampleSentence = (question.exampleSentence || '').trim()

  if (!word && !meaningCn && !correctedExampleSentence && !exampleSentence) {
    return null
  }

  return {
    word,
    meaningCn,
    exampleSentence,
    correctedExampleSentence,
  }
}

function clampMessages(messages: EnglishChatMessage[]) {
  return messages.slice(-10)
}

function normalizeAssistantMessage(content: string) {
  return content
    .replace(/([A-Za-z0-9.,!?;:)\]])([\u4e00-\u9fff])/g, '$1 $2')
    .replace(/([\u4e00-\u9fff])([A-Za-z(])/g, '$1 $2')
    .replace(/\s*(核心词义[:：]|记忆点[:：]|短例句[:：]|例句中文[:：]|翻译[:：]|规则[:：]|核心知识点[:：])/g, '\n$1')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function renderInlineMarkdown(content: string) {
  const nodes: ReactNode[] = []
  const pattern = /\*\*(.+?)\*\*/g
  let lastIndex = 0

  for (const match of content.matchAll(pattern)) {
    const index = match.index ?? 0
    if (index > lastIndex) {
      nodes.push(content.slice(lastIndex, index))
    }
    nodes.push(<strong key={`strong-${index}`}>{match[1]}</strong>)
    lastIndex = index + match[0].length
  }

  if (lastIndex < content.length) {
    nodes.push(content.slice(lastIndex))
  }

  return nodes.length > 0 ? nodes : [content]
}

function renderAssistantMessage(content: string) {
  const normalized = normalizeAssistantMessage(content)
  const paragraphs = normalized
    .split(/\n{2,}/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)

  return (
    <div className="quiz-ai-rich-text">
      {paragraphs.map((paragraph, paragraphIndex) => (
        <p key={`paragraph-${paragraphIndex}`}>
          {paragraph.split('\n').map((line, lineIndex) => (
            <Fragment key={`line-${paragraphIndex}-${lineIndex}`}>
              {lineIndex > 0 ? <br /> : null}
              {renderInlineMarkdown(line)}
            </Fragment>
          ))}
        </p>
      ))}
    </div>
  )
}

export function QuizAiAssistant({ token, question, onUnauthorized }: QuizAiAssistantProps) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<EnglishChatMessage[]>([])
  const [streaming, setStreaming] = useState(false)
  const [error, setError] = useState('')
  const [launcherImage, setLauncherImage] = useState(() => AI_ASSISTANT_IMAGES[0])
  const [launcherPosition, setLauncherPosition] = useState<{ x: number; y: number } | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const messageListRef = useRef<HTMLDivElement | null>(null)
  const dragStateRef = useRef<{
    pointerId: number
    startX: number
    startY: number
    originX: number
    originY: number
    moved: boolean
  } | null>(null)
  const suppressClickRef = useRef(false)

  const questionContext = buildQuestionContext(question)
  const questionKey = question?.attemptId ?? null
  const canAsk = Boolean(questionContext)
  const visibleMessages = messages.filter((message) => message.role === 'user' || message.role === 'assistant')

  useEffect(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setMessages([])
    setDraft('')
    setError('')
    setStreaming(false)
  }, [questionKey])

  useEffect(() => {
    const node = messageListRef.current
    if (!node) {
      return
    }
    node.scrollTop = node.scrollHeight
  }, [messages, streaming])

  useEffect(() => () => abortRef.current?.abort(), [])

  useEffect(() => {
    if (launcherPosition != null || typeof window === 'undefined') {
      return
    }
    const size = window.innerWidth <= 640 ? 96 : 120
    const margin = window.innerWidth <= 640 ? 12 : 28
    setLauncherPosition({
      x: Math.max(12, window.innerWidth - size - margin),
      y: Math.max(12, window.innerHeight - size - margin),
    })
  }, [launcherPosition])

  useEffect(() => {
    const pickRandomImage = (current?: string) => {
      const candidates = AI_ASSISTANT_IMAGES.filter((image) => image !== current)
      const pool = candidates.length > 0 ? candidates : AI_ASSISTANT_IMAGES
      return pool[Math.floor(Math.random() * pool.length)] ?? AI_ASSISTANT_IMAGES[0]
    }

    setLauncherImage((current) => pickRandomImage(current))
    const timer = window.setInterval(() => {
      setLauncherImage((current) => pickRandomImage(current))
    }, 30000)

    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    function handlePointerMove(event: PointerEvent) {
      const dragState = dragStateRef.current
      if (!dragState) {
        return
      }

      const deltaX = event.clientX - dragState.startX
      const deltaY = event.clientY - dragState.startY
      if (!dragState.moved && Math.hypot(deltaX, deltaY) > 6) {
        dragState.moved = true
        suppressClickRef.current = true
      }

      const size = window.innerWidth <= 640 ? 96 : 120
      const nextX = Math.min(Math.max(0, dragState.originX + deltaX), Math.max(0, window.innerWidth - size))
      const nextY = Math.min(Math.max(0, dragState.originY + deltaY), Math.max(0, window.innerHeight - size))
      setLauncherPosition({ x: nextX, y: nextY })
    }

    function clearDrag() {
      window.setTimeout(() => {
        suppressClickRef.current = false
      }, 0)
      dragStateRef.current = null
    }

    function handlePointerUp(event: PointerEvent) {
      const dragState = dragStateRef.current
      if (!dragState || dragState.pointerId !== event.pointerId) {
        return
      }
      clearDrag()
    }

    function handlePointerCancel() {
      if (!dragStateRef.current) {
        return
      }
      clearDrag()
    }

    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    window.addEventListener('pointercancel', handlePointerCancel)

    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
      window.removeEventListener('pointercancel', handlePointerCancel)
    }
  }, [])

  function closeAndClear() {
    abortRef.current?.abort()
    abortRef.current = null
    setOpen(false)
    setMessages([])
    setDraft('')
    setError('')
    setStreaming(false)
  }

  function stopStreaming() {
    abortRef.current?.abort()
    abortRef.current = null
    setStreaming(false)
  }

  async function handleSubmit() {
    const userPrompt = draft.trim()
    if (!userPrompt || streaming || !questionContext) {
      return
    }

    const history = clampMessages(messages)
    const nextMessages: EnglishChatMessage[] = [
      ...history,
      { role: 'user', content: userPrompt },
      { role: 'assistant', content: '' },
    ]
    const controller = new AbortController()
    abortRef.current = controller

    setOpen(true)
    setError('')
    setDraft('')
    setMessages(nextMessages)
    setStreaming(true)

    try {
      await searchApi.streamEnglishChat(
        {
          messages: history,
          questionContext,
          userPrompt,
        },
        {
          signal: controller.signal,
          token,
          onUnauthorized,
          onEvent: (event: EnglishChatStreamEvent) => {
            if (event.type === 'token') {
              setMessages((current) => {
                if (current.length === 0) {
                  return current
                }
                const updated = current.slice()
                const last = updated[updated.length - 1]
                if (!last || last.role !== 'assistant') {
                  return current
                }
                updated[updated.length - 1] = {
                  ...last,
                  content: last.content + (event.text || ''),
                }
                return updated
              })
              return
            }

            if (event.type === 'error') {
              setError(event.message || 'AI 助手暂时不可用，请稍后再试')
            }
          },
        },
      )
    } catch (exception) {
      if (controller.signal.aborted) {
        return
      }
      setError(exception instanceof Error ? exception.message : 'AI 助手暂时不可用，请稍后再试')
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null
      }
      setStreaming(false)
      setMessages((current) => {
        if (current.length === 0) {
          return current
        }
        const last = current[current.length - 1]
        if (last?.role === 'assistant' && !last.content.trim()) {
          return current.slice(0, -1)
        }
        return current
      })
    }
  }

  function handleComposerKeyDown(event: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || event.shiftKey) {
      return
    }
    event.preventDefault()
    void handleSubmit()
  }

  function handleLauncherPointerDown(event: React.PointerEvent<HTMLDivElement>) {
    if (launcherPosition == null) {
      return
    }

    event.preventDefault()
    dragStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: launcherPosition.x,
      originY: launcherPosition.y,
      moved: false,
    }
  }

  function handleLauncherClick() {
    if (suppressClickRef.current) {
      return
    }
    if (canAsk) {
      setOpen((current) => !current)
    }
  }

  function handleLauncherKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Enter' && event.key !== ' ') {
      return
    }
    event.preventDefault()
    handleLauncherClick()
  }

  if (!token) {
    return null
  }

  return (
    <>
      <div
        role="button"
        tabIndex={0}
        className={`quiz-ai-launcher${open ? ' is-open' : ''}`}
        onClick={handleLauncherClick}
        onPointerDown={handleLauncherPointerDown}
        onKeyDown={handleLauncherKeyDown}
        aria-disabled={!canAsk}
        title={canAsk ? '打开 AI 英语助手' : '开始背词后可提问当前单词'}
        style={launcherPosition ? { left: `${launcherPosition.x}px`, top: `${launcherPosition.y}px` } : undefined}
      >
        <img
          className="quiz-ai-launcher-image"
          src={launcherImage}
          alt="AI 英语助手"
          draggable={false}
          onDragStart={(event) => event.preventDefault()}
          onError={(event) => {
            if (event.currentTarget.src !== AI_ASSISTANT_IMAGES[0]) {
              event.currentTarget.src = AI_ASSISTANT_IMAGES[0]
            }
          }}
        />
      </div>

      {open ? (
        <section className="quiz-ai-panel" aria-label="AI 英语助手">
          <header className="quiz-ai-panel-head">
            <div>
              <strong>AI 英语助手</strong>
              <span>仅回答英语学习问题，默认结合当前题目解释。</span>
            </div>
            <button type="button" className="ghost quiz-ai-close" onClick={closeAndClear}>
              关闭并清空
            </button>
          </header>

          <div className="quiz-ai-context">
            <strong>当前题目上下文已自动带入</strong>
            <small>AI 会结合当前单词、释义和例句回答，但这里不展开显示。</small>
          </div>

          <div ref={messageListRef} className="quiz-ai-messages">
            {visibleMessages.length === 0 ? (
              <div className="quiz-ai-empty">
                <strong>可以直接问</strong>
                <span>这个词怎么记？这个例句怎么理解？和近义词有什么区别？</span>
              </div>
            ) : (
              visibleMessages.map((message, index) => (
                <article
                  key={`${message.role}-${index}-${message.content.slice(0, 24)}`}
                  className={`quiz-ai-message quiz-ai-message--${message.role}`}
                >
                  <span>{message.role === 'user' ? '你' : 'AI'}</span>
                  {message.role === 'assistant' ? renderAssistantMessage(message.content) : <p>{message.content}</p>}
                </article>
              ))
            )}
          </div>

          {error ? <div className="quiz-feedback quiz-feedback--wrong">{error}</div> : null}

          <div className="quiz-ai-composer">
            <textarea
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={handleComposerKeyDown}
              placeholder="问当前单词、例句、搭配、语法或记忆方法"
              disabled={streaming}
              rows={3}
            />
            <div className="quiz-ai-actions">
              {streaming ? (
                <button type="button" className="ghost" onClick={stopStreaming}>
                  停止
                </button>
              ) : null}
              <button type="button" className="primary" onClick={() => void handleSubmit()} disabled={!draft.trim() || streaming}>
                {streaming ? '回答中...' : '发送'}
              </button>
            </div>
          </div>
        </section>
      ) : null}
    </>
  )
}
