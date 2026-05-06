import type { WordDetail } from '../api/modules/search'
import { formatMeaningText, formatMultilineText } from '../utils/text'

interface WordDetailModalProps {
  detail: WordDetail
  loading: boolean
  onClose: () => void
  onReplayAudio: () => void
  onReplayExampleAudio: () => void
}

export function WordDetailModal({
  detail,
  loading,
  onClose,
  onReplayAudio,
  onReplayExampleAudio,
}: WordDetailModalProps) {
  const meaningText = formatMeaningText(detail.meaningCn)
  const correctedExampleText = formatMultilineText(detail.correctedExampleSentence)
  const chineseSentenceText = formatMultilineText(detail.chineseSentence)
  const hasCorrectedExample = Boolean(correctedExampleText)
  const hasChineseSentence = Boolean(chineseSentenceText)
  const canReplayExample = Boolean(detail.correctedExampleSentence)

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="modal-card" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <div className="panel-head detail-head">
          <div className="detail-title-block">
            <div className="detail-title-row">
              <h3>{detail.word}</h3>
              <button
                type="button"
                className="ghost detail-audio-button"
                onClick={onReplayAudio}
                disabled={loading}
                aria-label={`播放 ${detail.word} 发音`}
              >
                读音
              </button>
              <strong className="phonetic-text detail-phonetic">/{detail.phonetic || '-'}/</strong>
            </div>
          </div>
          <button type="button" className="ghost detail-close-button" onClick={onClose}>
            关闭
          </button>
        </div>

        <div className="card detail-meaning-card">
          <strong>释义</strong>
          <span className="meta-value multiline-text">{meaningText}</span>
        </div>

        <div className="list">
          <div className="card detail-section-card">
            <div className="detail-section-head">
              <strong>英文例句</strong>
              {canReplayExample ? (
                <button
                  type="button"
                  className="ghost detail-audio-button"
                  onClick={onReplayExampleAudio}
                  aria-label={`播放 ${detail.word} 的例句读音`}
                >
                  读音
                </button>
              ) : null}
            </div>
            <span className="multiline-text">{hasCorrectedExample ? correctedExampleText : '暂无英文例句'}</span>
          </div>
          <div className="card detail-section-card">
            <strong>中文释义</strong>
            <span className="multiline-text">{hasChineseSentence ? chineseSentenceText : '暂无中文释义'}</span>
          </div>
        </div>
      </section>
    </div>
  )
}
