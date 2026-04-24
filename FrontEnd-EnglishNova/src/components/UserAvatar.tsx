import { useState } from 'react'
import type { AuthUser } from '../api/modules/auth'

interface UserAvatarProps {
  user: AuthUser
  className?: string
}

export function UserAvatar({ user, className = '' }: UserAvatarProps) {
  const [failedAvatarUrl, setFailedAvatarUrl] = useState<string | null>(null)
  const avatarUrl = user.avatarUrl?.trim()
  const imageFailed = Boolean(avatarUrl && avatarUrl === failedAvatarUrl)
  const fallbackText = user.username.trim().slice(0, 1).toUpperCase() || 'U'

  return (
    <span className={`user-avatar ${className}`.trim()} aria-hidden="true">
      {avatarUrl && !imageFailed ? (
        <img src={avatarUrl} alt="" onError={() => setFailedAvatarUrl(avatarUrl)} />
      ) : (
        fallbackText
      )}
    </span>
  )
}
