import type { ImportPlatform, ImportPreset, ImportTask } from '../types'

interface ImportsViewProps {
  presets: ImportPreset[]
  selectedPlatform: ImportPlatform
  onPlatformChange: (platform: ImportPlatform) => void
  sourceName: string
  onSourceNameChange: (value: string) => void
  preset: ImportPreset | undefined
  onFileChange: (file: File | null) => void
  tasks: ImportTask[]
  onImport: () => void
}

export function ImportsView({
  presets,
  selectedPlatform,
  onPlatformChange,
  sourceName,
  onSourceNameChange,
  preset,
  onFileChange,
  tasks,
  onImport,
}: ImportsViewProps) {
  return (
    <div className="split">
      <div className="form">
        <label>
          <span>导入平台</span>
          <select
            value={selectedPlatform}
            onChange={(e) => onPlatformChange(e.target.value as ImportPlatform)}
          >
            {presets.map((item) => (
              <option key={item.platform} value={item.platform}>
                {item.title}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>词书名称</span>
          <input value={sourceName} onChange={(e) => onSourceNameChange(e.target.value)} />
        </label>
        <label>
          <span>导入文件</span>
          <input
            type="file"
            accept={
              preset
                ? preset.acceptedExtensions.map((ext) => `.${ext}`).join(',')
                : '.apkg'
            }
            onChange={(e) => onFileChange(e.target.files?.[0] ?? null)}
          />
        </label>
        {preset && (
          <div className="meta">
            {preset.description} · {preset.mappedFields.join(' / ')}
          </div>
        )}
        <button type="button" className="primary" onClick={() => void onImport()}>
          上传并导入
        </button>
      </div>
      <div className="list">
        {tasks.slice(0, 8).map((task) => (
          <div key={task.taskId} className="card">
            <strong>{task.sourceName}</strong>
            <span>
              {task.importedCards} / {task.estimatedCards}
            </span>
            <small>{task.status}</small>
          </div>
        ))}
      </div>
    </div>
  )
}
