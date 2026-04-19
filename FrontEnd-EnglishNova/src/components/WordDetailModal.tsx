import type { WordDetail } from '../api/modules/search'
import { formatMultilineText } from '../utils/text'

interface WordDetailModalProps {
  detail: WordDetail
  loading: boolean
  onClose: () => void
  onReplayAudio: () => void
}

export function WordDetailModal({ detail, loading, onClose, onReplayAudio }: WordDetailModalProps) {
  const meaningText = formatMultilineText(detail.meaningCn)
  const exampleText = formatMultilineText(detail.exampleSentence)
  const wordbookText = formatMultilineText(detail.wordbookName)

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="modal-card" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <div className="panel-head detail-head">
          <div className="detail-title-block">
            <div className="detail-title-row">
              <h3>{detail.word}</h3>
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
          <div className="card">
            <strong>例句</strong>
            <span className="multiline-text">{exampleText}</span>
          </div>
          <div className="card">
            <strong>词书</strong>
            <span className="multiline-text">{wordbookText}</span>
          </div>
        </div>

        <div className="detail-actions">
          <button type="button" className="primary detail-action-button" onClick={onReplayAudio} disabled={loading}>
            {loading ? '加载中...' : '播放发音'}
          </button>
        </div>
      </section>
    </div>
  )
}
