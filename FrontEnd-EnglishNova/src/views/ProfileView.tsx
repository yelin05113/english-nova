import { useCallback, useEffect, useRef, useState } from 'react'
import type {
  FormEvent,
  KeyboardEvent,
  PointerEvent as ReactPointerEvent,
} from 'react'
import { useAppStateContext } from '../context/AppStateContext'
import { UserAvatar } from '../components/UserAvatar'

const PREVIEW_SIZE = 560
const CROP_SIZE = 360
const OUTPUT_SIZE = 512
const MAX_ZOOM = 4
const FIXED_CROP = {
  x: (PREVIEW_SIZE - CROP_SIZE) / 2,
  y: (PREVIEW_SIZE - CROP_SIZE) / 2,
  size: CROP_SIZE,
}

interface ImageMetrics {
  naturalWidth: number
  naturalHeight: number
  baseScale: number
  baseWidth: number
  baseHeight: number
  baseLeft: number
  baseTop: number
}

interface ImageTransform {
  x: number
  y: number
  zoom: number
}

const INITIAL_TRANSFORM: ImageTransform = { x: 0, y: 0, zoom: 1 }

function createImageMetrics(naturalWidth: number, naturalHeight: number): ImageMetrics {
  const baseScale = Math.max(PREVIEW_SIZE / naturalWidth, PREVIEW_SIZE / naturalHeight)
  const baseWidth = naturalWidth * baseScale
  const baseHeight = naturalHeight * baseScale
  return {
    naturalWidth,
    naturalHeight,
    baseScale,
    baseWidth,
    baseHeight,
    baseLeft: (PREVIEW_SIZE - baseWidth) / 2,
    baseTop: (PREVIEW_SIZE - baseHeight) / 2,
  }
}

function minZoomForCrop(metrics: ImageMetrics) {
  return Math.max(FIXED_CROP.size / metrics.baseWidth, FIXED_CROP.size / metrics.baseHeight)
}

function clampTransform(next: ImageTransform, metrics: ImageMetrics): ImageTransform {
  const zoom = Math.min(Math.max(minZoomForCrop(metrics), next.zoom), MAX_ZOOM)
  const displayWidth = metrics.baseWidth * zoom
  const displayHeight = metrics.baseHeight * zoom
  const minX = FIXED_CROP.x + FIXED_CROP.size - metrics.baseLeft - displayWidth
  const maxX = FIXED_CROP.x - metrics.baseLeft
  const minY = FIXED_CROP.y + FIXED_CROP.size - metrics.baseTop - displayHeight
  const maxY = FIXED_CROP.y - metrics.baseTop

  return {
    zoom,
    x: Math.min(Math.max(next.x, minX), maxX),
    y: Math.min(Math.max(next.y, minY), maxY),
  }
}

async function createCroppedAvatar(file: File, transform: ImageTransform): Promise<File> {
  const imageUrl = URL.createObjectURL(file)
  try {
    const image = await new Promise<HTMLImageElement>((resolve, reject) => {
      const nextImage = new Image()
      nextImage.onload = () => resolve(nextImage)
      nextImage.onerror = () => reject(new Error('头像图片读取失败'))
      nextImage.src = imageUrl
    })

    const metrics = createImageMetrics(image.naturalWidth, image.naturalHeight)
    const scale = metrics.baseScale * transform.zoom
    const sourceX = (FIXED_CROP.x - metrics.baseLeft - transform.x) / scale
    const sourceY = (FIXED_CROP.y - metrics.baseTop - transform.y) / scale
    const sourceSize = FIXED_CROP.size / scale

    const canvas = document.createElement('canvas')
    canvas.width = OUTPUT_SIZE
    canvas.height = OUTPUT_SIZE
    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('浏览器不支持头像裁剪')
    }

    context.drawImage(image, sourceX, sourceY, sourceSize, sourceSize, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE)

    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob((nextBlob) => {
        if (nextBlob) {
          resolve(nextBlob)
          return
        }
        reject(new Error('头像裁剪失败'))
      }, 'image/png')
    })

    return new File([blob], 'avatar.png', { type: 'image/png' })
  } finally {
    URL.revokeObjectURL(imageUrl)
  }
}

export function ProfileView() {
  const { user, loading, handleUpdateProfile, handleUploadAvatar } = useAppStateContext()
  const [username, setUsername] = useState(user?.username ?? '')
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState('')
  const [avatarFile, setAvatarFile] = useState<File | null>(null)
  const [draftFile, setDraftFile] = useState<File | null>(null)
  const [draftUrl, setDraftUrl] = useState('')
  const [imageMetrics, setImageMetrics] = useState<ImageMetrics | null>(null)
  const [imageTransform, setImageTransform] = useState<ImageTransform>(INITIAL_TRANSFORM)
  const [saving, setSaving] = useState(false)
  const [cropping, setCropping] = useState(false)
  const cropperRef = useRef<HTMLDivElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const draggingRef = useRef(false)
  const dragStartRef = useRef({
    clientX: 0,
    clientY: 0,
    transform: INITIAL_TRANSFORM,
  })

  useEffect(() => {
    setUsername(user?.username ?? '')
  }, [user])

  useEffect(() => {
    if (!avatarFile) {
      setAvatarPreviewUrl('')
      return
    }

    const nextPreviewUrl = URL.createObjectURL(avatarFile)
    setAvatarPreviewUrl(nextPreviewUrl)
    return () => URL.revokeObjectURL(nextPreviewUrl)
  }, [avatarFile])

  useEffect(() => {
    if (!draftFile) {
      setDraftUrl('')
      setImageMetrics(null)
      setImageTransform(INITIAL_TRANSFORM)
      return
    }

    const nextDraftUrl = URL.createObjectURL(draftFile)
    setDraftUrl(nextDraftUrl)
    return () => URL.revokeObjectURL(nextDraftUrl)
  }, [draftFile])

  function openCropper(file: File | null) {
    if (!file) return
    setDraftFile(file)
    setImageMetrics(null)
    setImageTransform(INITIAL_TRANSFORM)
  }

  const closeCropper = useCallback(() => {
    setDraftFile(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }, [])

  const confirmCrop = useCallback(async () => {
    if (!draftFile || !imageMetrics || cropping) return
    setCropping(true)
    try {
      const croppedFile = await createCroppedAvatar(draftFile, clampTransform(imageTransform, imageMetrics))
      setAvatarFile(croppedFile)
      closeCropper()
    } finally {
      setCropping(false)
    }
  }, [closeCropper, cropping, draftFile, imageMetrics, imageTransform])

  useEffect(() => {
    if (!draftFile) return

    function onModalKeyDown(event: globalThis.KeyboardEvent) {
      if (event.key === 'Enter') {
        event.preventDefault()
        void confirmCrop()
      }
      if (event.key === 'Escape') {
        event.preventDefault()
        closeCropper()
      }
    }

    window.addEventListener('keydown', onModalKeyDown)
    return () => window.removeEventListener('keydown', onModalKeyDown)
  }, [draftFile, confirmCrop, closeCropper])

  useEffect(() => {
    if (!draftFile || !imageMetrics) return
    const metrics = imageMetrics

    function onWindowPointerMove(event: PointerEvent) {
      if (!draggingRef.current) return
      event.preventDefault()
      const cropperBounds = cropperRef.current?.getBoundingClientRect()
      if (!cropperBounds) return

      const scale = PREVIEW_SIZE / cropperBounds.width
      const dx = (event.clientX - dragStartRef.current.clientX) * scale
      const dy = (event.clientY - dragStartRef.current.clientY) * scale
      setImageTransform(
        clampTransform(
          {
            ...dragStartRef.current.transform,
            x: dragStartRef.current.transform.x + dx,
            y: dragStartRef.current.transform.y + dy,
          },
          metrics,
        ),
      )
    }

    function onWindowPointerUp() {
      draggingRef.current = false
    }

    window.addEventListener('pointermove', onWindowPointerMove, { passive: false })
    window.addEventListener('pointerup', onWindowPointerUp)
    window.addEventListener('pointercancel', onWindowPointerUp)
    return () => {
      window.removeEventListener('pointermove', onWindowPointerMove)
      window.removeEventListener('pointerup', onWindowPointerUp)
      window.removeEventListener('pointercancel', onWindowPointerUp)
    }
  }, [draftFile, imageMetrics])

  useEffect(() => {
    if (!draftFile || !imageMetrics) return
    const cropper = cropperRef.current
    if (!cropper) return

    function onWheel(event: WheelEvent) {
      event.preventDefault()
      const direction = event.deltaY < 0 ? 1 : -1
      const factor = direction > 0 ? 1.08 : 1 / 1.08
      zoomImage(imageTransform.zoom * factor)
    }

    cropper.addEventListener('wheel', onWheel, { passive: false })
    return () => cropper.removeEventListener('wheel', onWheel)
  }, [draftFile, imageMetrics, imageTransform.zoom])

  if (!user) return null
  const currentUser = user

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      if (avatarFile) {
        await handleUploadAvatar(avatarFile)
        setAvatarFile(null)
        if (fileInputRef.current) {
          fileInputRef.current.value = ''
        }
      }
      await handleUpdateProfile({
        username: username.trim(),
        avatarUrl: currentUser.avatarUrl,
      })
    } finally {
      setSaving(false)
    }
  }

  const previewUser = {
    ...currentUser,
    username: username.trim() || currentUser.username,
    avatarUrl: avatarPreviewUrl || currentUser.avatarUrl,
  }

  function onImageLoad(event: React.SyntheticEvent<HTMLImageElement>) {
    const nextMetrics = createImageMetrics(event.currentTarget.naturalWidth, event.currentTarget.naturalHeight)
    setImageMetrics(nextMetrics)
    setImageTransform(clampTransform(INITIAL_TRANSFORM, nextMetrics))
  }

  function onCropperPointerDown(event: ReactPointerEvent<HTMLDivElement>) {
    if (!imageMetrics) return
    event.preventDefault()
    draggingRef.current = true
    dragStartRef.current = {
      clientX: event.clientX,
      clientY: event.clientY,
      transform: imageTransform,
    }
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  function onCropperPointerUp(event: ReactPointerEvent<HTMLDivElement>) {
    draggingRef.current = false
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
  }

  function zoomImage(nextZoom: number) {
    if (!imageMetrics) return
    const cropCenterX = FIXED_CROP.x + FIXED_CROP.size / 2
    const cropCenterY = FIXED_CROP.y + FIXED_CROP.size / 2
    setImageTransform((current) => {
      const zoom = Math.min(Math.max(minZoomForCrop(imageMetrics), nextZoom), MAX_ZOOM)
      const currentRelativeX = (cropCenterX - imageMetrics.baseLeft - current.x) / (imageMetrics.baseWidth * current.zoom)
      const currentRelativeY = (cropCenterY - imageMetrics.baseTop - current.y) / (imageMetrics.baseHeight * current.zoom)
      return clampTransform(
        {
          zoom,
          x: cropCenterX - imageMetrics.baseLeft - currentRelativeX * imageMetrics.baseWidth * zoom,
          y: cropCenterY - imageMetrics.baseTop - currentRelativeY * imageMetrics.baseHeight * zoom,
        },
        imageMetrics,
      )
    })
  }

  function moveImageByKeyboard(event: KeyboardEvent<HTMLDivElement>) {
    if (!imageMetrics) return
    const step = event.shiftKey ? 28 : 10
    const movement: Record<string, [number, number]> = {
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      void confirmCrop()
      return
    }
    if (event.key === '+' || event.key === '=') {
      event.preventDefault()
      zoomImage(imageTransform.zoom * 1.08)
      return
    }
    if (event.key === '-' || event.key === '_') {
      event.preventDefault()
      zoomImage(imageTransform.zoom / 1.08)
      return
    }
    const delta = movement[event.key]
    if (!delta) return
    event.preventDefault()
    setImageTransform((current) =>
      clampTransform({ ...current, x: current.x + delta[0], y: current.y + delta[1] }, imageMetrics),
    )
  }

  const cropPercent = {
    x: `${(FIXED_CROP.x / PREVIEW_SIZE) * 100}%`,
    y: `${(FIXED_CROP.y / PREVIEW_SIZE) * 100}%`,
    size: `${(FIXED_CROP.size / PREVIEW_SIZE) * 100}%`,
    afterX: `${((FIXED_CROP.x + FIXED_CROP.size) / PREVIEW_SIZE) * 100}%`,
    afterY: `${((FIXED_CROP.y + FIXED_CROP.size) / PREVIEW_SIZE) * 100}%`,
    remainingX: `${((PREVIEW_SIZE - FIXED_CROP.x - FIXED_CROP.size) / PREVIEW_SIZE) * 100}%`,
    remainingY: `${((PREVIEW_SIZE - FIXED_CROP.y - FIXED_CROP.size) / PREVIEW_SIZE) * 100}%`,
  }
  const imageStyle = imageMetrics
    ? {
        left: `${((imageMetrics.baseLeft + imageTransform.x) / PREVIEW_SIZE) * 100}%`,
        top: `${((imageMetrics.baseTop + imageTransform.y) / PREVIEW_SIZE) * 100}%`,
        width: `${((imageMetrics.baseWidth * imageTransform.zoom) / PREVIEW_SIZE) * 100}%`,
        height: `${((imageMetrics.baseHeight * imageTransform.zoom) / PREVIEW_SIZE) * 100}%`,
      }
    : undefined

  return (
    <section className="profile-layout">
      <article className="panel profile-hero-panel">
        <div className="profile-avatar-stage">
          <UserAvatar user={previewUser} className="profile-avatar" />
        </div>
        <div>
          <p className="eyebrow">个人资料</p>
          <h2 className="profile-title">{previewUser.username}</h2>
          <p className="profile-subtitle">UID：{currentUser.id}</p>
        </div>
      </article>

      <article className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">账号资料</p>
            <h3>个人中心</h3>
          </div>
        </div>

        <form className="form profile-form" onSubmit={(event) => void onSubmit(event)}>
          <label>
            <span>姓名</span>
            <input
              id="profile-username"
              name="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              maxLength={32}
            />
          </label>

          <div className="profile-upload-box">
            <label className="profile-file-picker">
              <span>更改头像</span>
              <strong>{avatarFile ? '已选择新头像' : '选择头像图片'}</strong>
              <small>{avatarFile ? '点击保存资料后生效' : '支持 JPG、PNG、WebP'}</small>
              <input
                ref={fileInputRef}
                id="profile-avatar"
                name="avatar"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(event) => openCropper(event.target.files?.[0] ?? null)}
              />
            </label>
          </div>

          <div className="profile-meta-grid">
            <div className="meta">
              <span className="meta-label">用户 ID</span>
              <span className="meta-value">{currentUser.id}</span>
            </div>
          </div>

          <button type="submit" className="primary profile-save-button" disabled={saving || loading}>
            {saving || loading ? '保存中...' : '保存资料'}
          </button>
        </form>
      </article>

      {draftFile && (
        <div className="modal-backdrop avatar-crop-backdrop" role="dialog" aria-modal="true" aria-label="选择头像区域">
          <div className="modal-card avatar-crop-modal">
            <div className="panel-head avatar-crop-head">
              <div>
                <p className="eyebrow">头像裁剪</p>
                <h3>选择头像区域</h3>
              </div>
              <button type="button" className="ghost" onClick={closeCropper}>
                取消
              </button>
            </div>

            <div
              className="avatar-cropper"
              ref={cropperRef}
              role="application"
              tabIndex={0}
              onKeyDown={moveImageByKeyboard}
              onPointerDown={onCropperPointerDown}
              onPointerUp={onCropperPointerUp}
              onPointerCancel={onCropperPointerUp}
            >
              {draftUrl && <img src={draftUrl} alt="" draggable={false} onLoad={onImageLoad} style={imageStyle} />}
              <div className="avatar-crop-grid" aria-hidden="true" />
              <div className="avatar-crop-dim avatar-crop-dim-top" style={{ height: cropPercent.y }} />
              <div
                className="avatar-crop-dim avatar-crop-dim-bottom"
                style={{ top: cropPercent.afterY, height: cropPercent.remainingY }}
              />
              <div
                className="avatar-crop-dim avatar-crop-dim-left"
                style={{ top: cropPercent.y, width: cropPercent.x, height: cropPercent.size }}
              />
              <div
                className="avatar-crop-dim avatar-crop-dim-right"
                style={{
                  top: cropPercent.y,
                  left: cropPercent.afterX,
                  width: cropPercent.remainingX,
                  height: cropPercent.size,
                }}
              />
              <div
                className="avatar-crop-ring"
                aria-hidden="true"
                style={{ left: cropPercent.x, top: cropPercent.y, width: cropPercent.size, height: cropPercent.size }}
              />
            </div>

            <div className="avatar-crop-actions">
              <button type="button" className="ghost" onClick={closeCropper}>
                取消
              </button>
              <button type="button" className="primary" onClick={() => void confirmCrop()} disabled={cropping}>
                {cropping ? '处理中...' : '确认'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
