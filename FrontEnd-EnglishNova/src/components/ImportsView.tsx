import type { ImportPlatform } from '../types/types'
import { useAppStateContext } from '../context/AppStateContext'

export function ImportsView() {
  const {
    presets,
    selectedPlatform,
    setSelectedPlatform,
    sourceName,
    setSourceName,
    preset,
    setSelectedFile,
    tasks,
    handleImport,
  } = useAppStateContext()

  const onPlatformChange = (platform: ImportPlatform) => setSelectedPlatform(platform)
  const onSourceNameChange = (value: string) => setSourceName(value)
  const onFileChange = (file: File | null) => setSelectedFile(file)
  const onImport = () => void handleImport()
  return (
    <div className="flex gap-4 items-start max-[1120px]:grid">
      <div className="flex-1 grid gap-[14px]">
        <label className="grid gap-2">
          <span className="text-forest-deep">导入平台</span>
          <select
            className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
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
        <label className="grid gap-2">
          <span className="text-forest-deep">词书名称</span>
          <input
            className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
            value={sourceName}
            onChange={(e) => onSourceNameChange(e.target.value)}
          />
        </label>
        <label className="grid gap-2">
          <span className="text-forest-deep">导入文件</span>
          <input
            className="w-full border border-[rgba(88,112,90,0.16)] rounded-2xl px-4 py-[14px] bg-[rgba(255,252,247,0.94)] text-forest-deep outline-none focus:border-[rgba(76,103,78,0.36)] focus:shadow-[0_0_0_4px_rgba(134,165,128,0.14)]"
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
          <div className="grid gap-1.5 px-[14px] py-3 rounded-2xl bg-[rgba(226,234,224,0.82)] text-muted">
            {preset.description} · {preset.mappedFields.join(' / ')}
          </div>
        )}
        <button
          type="button"
          className="border-none bg-gradient-to-br from-[#385d4b] to-[#6f8c67] text-[#faf4ea] rounded-2xl px-4 py-[13px] cursor-pointer transition-transform duration-[160ms] hover:-translate-y-0.5"
          onClick={() => void onImport()}
        >
          上传并导入
        </button>
      </div>
      <div className="flex-1 grid gap-[14px]">
        {tasks.slice(0, 8).map((task) => (
          <div
            key={task.taskId}
            className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[22px] p-[18px] grid gap-2"
          >
            <strong className="text-forest-deep">{task.sourceName}</strong>
            <span>
              {task.importedCards} / {task.estimatedCards}
            </span>
            <small className="text-muted">{task.status}</small>
          </div>
        ))}
      </div>
    </div>
  )
}
