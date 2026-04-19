import { NavLink } from 'react-router'
import { navItems } from '../constants'

export function NavSidebar() {
  return (
    <aside className="panel nav">
      {navItems.map((item) => (
        <NavLink
          key={item.path}
          to={item.path}
          className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}
        >
          {item.label}
        </NavLink>
      ))}
    </aside>
  )
}
