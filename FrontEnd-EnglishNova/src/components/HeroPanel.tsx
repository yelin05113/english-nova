import type { StudyAgenda, WordSearchResponse, WordbookSummary } from '../types'

interface HeroPanelProps {
  wordbooks: WordbookSummary[]
  agenda: StudyAgenda | null
  searchResult: WordSearchResponse
}

export function HeroPanel({ wordbooks, agenda, searchResult }: HeroPanelProps) {
  return (
    <section className="hero">
      <div>
        <p className="eyebrow">个人导入 / 私有隔离 / 四选一斩词</p>
        <h2>导入的词，只给当前用户看。</h2>
        <p>登录后即可导入 Anki 词书、按词书斩词，并搜索公共词库和自己的私有词。</p>
      </div>
      <div className="hero-grid">
        <article>
          <span>词书</span>
          <strong>{wordbooks.length}</strong>
        </article>
        <article>
          <span>今日任务</span>
          <strong>{agenda ? agenda.newCards + agenda.reviewCards : 0}</strong>
        </article>
        <article>
          <span>搜索结果</span>
          <strong>{searchResult.publicHits.length + searchResult.myHits.length}</strong>
        </article>
      </div>
    </section>
  )
}
