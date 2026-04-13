import { navItems } from '../constants'
import type { ViewKey } from '../types'

interface NavSidebarProps {
  view: ViewKey
  onSwitch: (key: ViewKey) => void
}

export function NavSidebar({ view, onSwitch }: NavSidebarProps) {
  return (
    <aside className="panel nav">
      {navItems.map((item) => (
        <button
          key={item.key}
          type="button"
          className={view === item.key ? 'nav-item active' : 'nav-item'}
          onClick={() => onSwitch(item.key)}
        >
          {item.label}
        </button>
      ))}
    </aside>
  )
}
