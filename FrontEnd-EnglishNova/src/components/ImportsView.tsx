import type { ImportPlatform } from '../api/modules/imports'
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
    handleImport,
  } = useAppStateContext()

  const onPlatformChange = (platform: ImportPlatform) => setSelectedPlatform(platform)
  const onSourceNameChange = (value: string) => setSourceName(value)
  const onFileChange = (file: File | null) => setSelectedFile(file)
  const onImport = () => void handleImport()
  return (
    <div className="split">
      <div className="form">
        <label>
          <span>导入平台</span>
          <select
            id="import-platform"
            name="platform"
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
          <input
            id="import-source-name"
            name="sourceName"
            value={sourceName}
            onChange={(e) => onSourceNameChange(e.target.value)}
          />
        </label>
        <label>
          <span>导入文件</span>
          <input
            id="import-file"
            name="file"
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
    </div>
  )
}
