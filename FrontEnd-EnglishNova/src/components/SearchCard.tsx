import type { SearchHit } from '../types/types'

interface SearchCardProps {
  item: SearchHit
  onOpen: (item: SearchHit) => void
}

export function SearchCard({ item, onOpen }: SearchCardProps) {
  return (
    <button
      type="button"
      className="w-full text-left border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[22px] p-[18px] grid gap-2 cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5"
      onClick={() => onOpen(item)}
    >
      <div className="flex items-start justify-between gap-3">
        <strong className="text-forest-deep">{item.word}</strong>
        <div className="grid justify-items-end gap-1">
          <span className="font-['Segoe_UI_Symbol',Cambria,'Times_New_Roman',serif] tracking-[0.02em] text-[0.98rem]">
            {item.phonetic || '-'}
          </span>
          <span className="text-[0.76rem] text-muted">匹配 {item.matchPercent}%</span>
        </div>
      </div>
      <span className="text-forest-deep leading-[1.6]">{item.meaningCn}</span>
      <small className="leading-[1.5] text-muted">
        {item.source} | {item.importSource} | {item.exampleSentence}
      </small>
    </button>
  )
}
