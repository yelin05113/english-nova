import type { StudyAgenda, StudyProgress } from '../types'

interface ProgressViewProps {
  progress: StudyProgress | null
  agenda: StudyAgenda | null
}

export function ProgressView({ progress, agenda }: ProgressViewProps) {
  return (
    <div className="split">
      <div className="list">
        <div className="card">
          <strong>总词数 {progress?.totalWords ?? 0}</strong>
          <span>已答题数 {progress?.answeredQuestions ?? 0}</span>
          <small>正确率 {progress?.accuracyRate ?? 0}%</small>
        </div>
        <div className="card">
          <strong>今日任务</strong>
          <span>
            新词 {agenda?.newCards ?? 0} / 复习 {agenda?.reviewCards ?? 0}
          </span>
          <small>建议学习 {agenda?.estimatedMinutes ?? 0} 分钟</small>
        </div>
      </div>
      <div className="list">
        {(agenda?.focusAreas ?? []).map((item) => (
          <div key={item} className="card">
            <strong>当前建议</strong>
            <span>{item}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
