import type { WordDetail } from '../types/types'

interface WordDetailModalProps {
  detail: WordDetail
  loading: boolean
  onClose: () => void
  onReplayAudio: () => void
}

export function WordDetailModal({ detail, loading, onClose, onReplayAudio }: WordDetailModalProps) {
  return (
    <div
      className="fixed inset-0 bg-[rgba(31,42,33,0.38)] grid place-items-center p-5 z-40"
      role="presentation"
      onClick={onClose}
    >
      <section
        className="w-[min(680px,100%)] rounded-[28px] border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.97)] shadow-[0_24px_64px_rgba(36,49,38,0.22)] p-6"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between gap-4 mb-4">
          <div>
            <p className="m-0 text-muted uppercase tracking-[0.18em] text-[0.72rem]">{detail.source}</p>
            <h3>{detail.word}</h3>
          </div>
          <button
            type="button"
            className="border border-[rgba(88,112,90,0.16)] bg-[rgba(255,252,247,0.88)] text-forest-deep rounded-2xl px-4 py-[13px] cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5"
            onClick={onClose}
          >
            关闭
          </button>
        </div>

        {/* Detail grid */}
        <div className="grid grid-cols-3 gap-[14px] mb-[14px] max-[720px]:grid-cols-1">
          <div className="grid gap-1.5 px-[14px] py-3 rounded-2xl bg-[rgba(226,234,224,0.82)]">
            <span className="text-[0.78rem] tracking-[0.08em] uppercase text-muted">音标</span>
            <strong className="font-['Segoe_UI_Symbol',Cambria,'Times_New_Roman',serif] tracking-[0.02em]">
              {detail.phonetic || '-'}
            </strong>
          </div>
          <div className="grid gap-1.5 px-[14px] py-3 rounded-2xl bg-[rgba(226,234,224,0.82)]">
            <span className="text-[0.78rem] tracking-[0.08em] uppercase text-muted">释义</span>
            <span className="text-forest-deep leading-[1.6]">{detail.meaningCn}</span>
          </div>
          <div className="grid gap-1.5 px-[14px] py-3 rounded-2xl bg-[rgba(226,234,224,0.82)]">
            <span className="text-[0.78rem] tracking-[0.08em] uppercase text-muted">来源</span>
            <strong className="text-forest-deep">{detail.importSource}</strong>
            <span className="text-forest-deep leading-[1.6]">{detail.sourceName}</span>
          </div>
        </div>

        {/* Cards list */}
        <div className="grid gap-[14px] mb-4">
          {[
            { label: '例句', value: detail.exampleSentence },
            { label: '分类', value: detail.category },
            { label: '词书', value: detail.wordbookName },
          ].map(({ label, value }) => (
            <div
              key={label}
              className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[22px] p-[18px] grid gap-2"
            >
              <strong className="text-forest-deep">{label}</strong>
              <span>{value}</span>
            </div>
          ))}
        </div>

        {/* Toolbar */}
        <div className="flex items-center justify-between gap-4">
          <div className="flex gap-2">
            <span className="px-3 py-2 rounded-full bg-[rgba(226,234,224,0.88)] text-muted">
              {detail.visibility}
            </span>
            <span className="px-3 py-2 rounded-full bg-[rgba(226,234,224,0.88)] text-muted">
              难度 {detail.difficulty}
            </span>
          </div>
          <button
            type="button"
            className="border-none bg-gradient-to-br from-[#385d4b] to-[#6f8c67] text-[#faf4ea] rounded-2xl px-4 py-[13px] cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5 disabled:opacity-60 disabled:cursor-default disabled:translate-y-0"
            onClick={onReplayAudio}
            disabled={loading}
          >
            {loading ? '加载中...' : '播放发音'}
          </button>
        </div>
      </section>
    </div>
  )
}
