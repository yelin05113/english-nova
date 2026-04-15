import type { StudyAgenda, WordSearchResponse, WordbookSummary } from '../types/types'

interface HeroPanelProps {
  wordbooks: WordbookSummary[]
  agenda: StudyAgenda | null
  searchResult: WordSearchResponse
}

export function HeroPanel({ wordbooks, agenda, searchResult }: HeroPanelProps) {
  return (
    <section className="border border-[rgba(82,107,84,0.14)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[30px] mt-5 flex items-stretch justify-between p-[30px] bg-[radial-gradient(circle_at_top_left,rgba(217,232,205,0.92),transparent_34%),linear-gradient(135deg,rgba(249,245,237,0.96),rgba(226,234,224,0.88))]">
      <div>
        <p className="m-0 text-muted uppercase tracking-[0.18em] text-[0.72rem]">个人导入 / 私有隔离 / 四选一斩词</p>
        <h2 className="my-2.5 text-[clamp(2.8rem,5vw,5rem)] leading-[0.92] tracking-[-0.05em] text-forest-deep">
          导入的词，只给当前用户看。
        </h2>
        <p className="text-ink-soft">登录后即可导入 Anki 词书、按词书斩词，并搜索公共词库和自己的私有词。</p>
      </div>
      <div className="flex flex-wrap gap-4 content-center">
        <article className="min-w-[150px] rounded-[22px] p-[18px]">
          <span className="text-muted">词书</span>
          <strong className="block text-forest-deep text-[1.8rem] mt-2.5">{wordbooks.length}</strong>
        </article>
        <article className="min-w-[150px] rounded-[22px] p-[18px]">
          <span className="text-muted">今日任务</span>
          <strong className="block text-forest-deep text-[1.8rem] mt-2.5">
            {agenda ? agenda.newCards + agenda.reviewCards : 0}
          </strong>
        </article>
        <article className="min-w-[150px] rounded-[22px] p-[18px]">
          <span className="text-muted">搜索结果</span>
          <strong className="block text-forest-deep text-[1.8rem] mt-2.5">
            {searchResult.publicHits.length + searchResult.myHits.length}
          </strong>
        </article>
      </div>
    </section>
  )
}
