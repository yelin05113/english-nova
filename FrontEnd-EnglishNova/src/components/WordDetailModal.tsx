import type { WordDetail } from '../types'

interface WordDetailModalProps {
  detail: WordDetail
  loading: boolean
  onClose: () => void
  onReplayAudio: () => void
}

export function WordDetailModal({ detail, loading, onClose, onReplayAudio }: WordDetailModalProps) {
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="modal-card" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <div className="panel-head">
          <div>
            <p className="eyebrow">{detail.source}</p>
            <h3>{detail.word}</h3>
          </div>
          <button type="button" className="ghost" onClick={onClose}>
            关闭
          </button>
        </div>

        <div className="detail-grid">
          <div className="meta">
            <span className="meta-label">音标</span>
            <strong className="phonetic-text">{detail.phonetic || '-'}</strong>
          </div>
          <div className="meta">
            <span className="meta-label">释义</span>
            <span className="meta-value">{detail.meaningCn}</span>
          </div>
          <div className="meta">
            <span className="meta-label">来源</span>
            <strong>{detail.importSource}</strong>
            <span className="meta-value">{detail.sourceName}</span>
          </div>
        </div>

        <div className="list">
          <div className="card">
            <strong>例句</strong>
            <span>{detail.exampleSentence}</span>
          </div>
          <div className="card">
            <strong>分类</strong>
            <span>{detail.category}</span>
          </div>
          <div className="card">
            <strong>词书</strong>
            <span>{detail.wordbookName}</span>
          </div>
        </div>

        <div className="toolbar">
          <span className="badge">{detail.visibility}</span>
          <span className="badge">难度 {detail.difficulty}</span>
          <button type="button" className="primary" onClick={onReplayAudio} disabled={loading}>
            {loading ? '加载中...' : '播放发音'}
          </button>
        </div>
      </section>
    </div>
  )
}
