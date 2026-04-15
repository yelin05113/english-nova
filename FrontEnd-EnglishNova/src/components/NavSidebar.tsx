import { NavLink } from 'react-router'
import { navItems } from '../constants'

export function NavSidebar() {
  return (
    <aside className="border border-[rgba(82,107,84,0.14)] bg-[rgba(252,248,241,0.78)] shadow-[0_20px_44px_rgba(70,92,72,0.08)] rounded-[30px] p-[22px] w-[220px] sticky top-[18px]">
      {navItems.map((item) => (
        <NavLink
          key={item.path}
          to={item.path}
          end={item.path === '/'}
          className={({ isActive }) =>
            `block w-full text-left border rounded-[18px] px-4 py-[14px] mt-[10px] cursor-pointer transition-[transform,border-color,background] duration-[160ms] hover:-translate-y-0.5 ${
              isActive
                ? 'border-[rgba(79,105,82,0.24)] bg-gradient-to-br from-[rgba(217,232,205,0.78)] to-[rgba(255,252,247,0.94)] text-forest-deep'
                : 'border-transparent bg-[rgba(232,239,228,0.78)] text-forest-deep'
            }`
          }
        >
          {item.label}
        </NavLink>
      ))}
    </aside>
  )
}
