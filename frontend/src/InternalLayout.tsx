import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

export type InternalIconName = 'dashboard' | 'users' | 'settings' | 'audit'
export interface InternalNavItem<T extends string> {
  id: T
  label: string
  icon: InternalIconName
}

function NavIcon({ name }: { name: InternalIconName }) {
  const common = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8,
    strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const }
  return <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" {...common}>
    {name === 'dashboard' && <><rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" /></>}
    {name === 'users' && <><circle cx="9" cy="8" r="3" /><path d="M3.5 20v-2a5.5 5.5 0 0 1 11 0v2M17 5a3 3 0 0 1 0 6M18 14a5 5 0 0 1 2.5 4.5V20" /></>}
    {name === 'settings' && <><circle cx="12" cy="12" r="3" /><path d="m19.4 15 .5 1.5-2 2-.5-.5a2 2 0 0 0-2.2-.4l-.8.4V21h-4.8v-3l-.8-.4a2 2 0 0 0-2.2.4l-.5.5-2-2L4.6 15a2 2 0 0 0-1.3-1.8L2.5 13v-2l.8-.2A2 2 0 0 0 4.6 9l-.5-1.5 2-2 .5.5a2 2 0 0 0 2.2.4l.8-.4V3h4.8v3l.8.4a2 2 0 0 0 2.2-.4l.5-.5 2 2-.5 1.5a2 2 0 0 0 1.3 1.8l.8.2v2l-.8.2a2 2 0 0 0-1.3 1.8Z" /></>}
    {name === 'audit' && <><path d="M7 3h9l4 4v14H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM16 3v5h4M9 12h7M9 16h7" /></>}
  </svg>
}

export default function InternalLayout<T extends string>({ items, active, onNavigate, title, subtitle,
  children }: { items: InternalNavItem<T>[]; active: T; onNavigate: (id: T) => void;
  title: string; subtitle: string; children: ReactNode }) {
  return <div className="internal-shell">
    <aside className="internal-sidebar" aria-label="Điều hướng quản lý nội bộ">
      <div className="internal-sidebar-top"><Link to="/" className="internal-logo">FORME<span>QUẢN LÝ NỘI BỘ</span></Link>
        <div className="internal-workspace"><span className="internal-workspace-mark">F</span>
          <div><strong>Không gian quản trị</strong><small>Hệ thống cửa hàng</small></div></div>
        <p className="internal-nav-caption">ĐIỀU HƯỚNG</p>
        <nav className="internal-nav" aria-label="Mục quản trị">
          {items.map((item) => <button key={item.id} type="button" className={active === item.id ? 'active' : ''}
            aria-current={active === item.id ? 'page' : undefined} onClick={() => onNavigate(item.id)}>
            <NavIcon name={item.icon} /><span>{item.label}</span></button>)}
        </nav>
      </div>
      <div className="internal-sidebar-bottom"><Link to="/me">Hồ sơ của tôi</Link>
        <Link to="/">← Về cửa hàng</Link></div>
    </aside>
    <div className="internal-content"><header className="internal-topbar"><div>
      <span className="internal-topbar-kicker">KHÔNG GIAN QUẢN TRỊ</span><strong>{title}</strong></div>
      <Link to="/me" className="internal-profile-link" aria-label="Mở hồ sơ cá nhân">QT</Link></header>
      <main className="internal-main"><div className="internal-page-heading"><p className="auth-kicker">QUẢN LÝ NỘI BỘ</p>
        <h1>{title}</h1><p>{subtitle}</p></div>{children}</main>
    </div>
  </div>
}
