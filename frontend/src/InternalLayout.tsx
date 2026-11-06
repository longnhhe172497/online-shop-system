import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import SignOutButton from './SignOutButton'
import './logistics.css'

export type InternalIconName = 'dashboard' | 'users' | 'settings' | 'audit' | 'tag' | 'box' | 'folder'
export interface InternalNavItem<T extends string> {
  id: T
  label: string
  icon: InternalIconName
}

const TODAY_LABEL = new Date().toLocaleDateString('vi-VN', {
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
})

function InternalNavIcon({ name }: { name: InternalIconName }) {
  return <svg className="staff-tab-icon" viewBox="0 0 24 24" aria-hidden="true">
    {name === 'dashboard' && <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>}
    {name === 'users' && <><circle cx="9" cy="8" r="3" /><path d="M3.5 20v-1a5.5 5.5 0 0 1 11 0v1M16 5.5a3 3 0 0 1 0 5.5M17 14a5 5 0 0 1 3.5 4.8V20" /></>}
    {name === 'settings' && <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z" /></>}
    {name === 'audit' && <><path d="M7 3h10l3 3v15H4V3h3Z" /><path d="M8 9h8M8 13h8M8 17h5" /></>}
    {name === 'tag' && <><path d="m3 12 9 9 9-9-9-9H3v9Z" /><circle cx="8" cy="8" r="1.5" /></>}
    {name === 'box' && <><path d="m4 7 8-4 8 4v10l-8 4-8-4V7Z" /><path d="m4 7 8 4 8-4M12 11v10" /></>}
    {name === 'folder' && <path d="M3 6h7l2 2h9v11H3V6Z" />}
  </svg>
}

export default function InternalLayout<T extends string>({ items, active, onNavigate, title, subtitle,
  roleName, children }: { items: InternalNavItem<T>[]; active: T; onNavigate: (id: T) => void;
  roleName: string; title: string; subtitle: string; children: ReactNode }) {
  return <div className="staff-root internal-staff-root">
    <header className="staff-header">
      <div className="staff-nav">
        <Link className="staff-brand" to="/" aria-label="FORME - Về cửa hàng">
          <span>FORME</span><small>QUẢN LÝ NỘI BỘ</small>
        </Link>
        <div className="staff-navicons">
          <span className="lg-chip lg-info">{roleName}</span>
          <Link className="staff-iconbtn" to="/" aria-label="Về cửa hàng" title="Về cửa hàng">
            <svg className="staff-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M3 11.5 12 4l9 7.5" /><path d="M5.5 10.5V21h13V10.5M9 21v-6h6v6" />
            </svg>
          </Link>
          <Link className="staff-iconbtn" to="/me" aria-label="Hồ sơ của tôi" title="Hồ sơ của tôi">
            <svg className="staff-icon" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="8" r="4" /><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1" />
            </svg>
          </Link>
          <SignOutButton className="staff-signout" />
        </div>
      </div>
    </header>
    <main className="staff-shell internal-main">
      <div className="staff-pagehead">
        <div><div className="staff-eyebrow">QUẢN LÝ NỘI BỘ</div>
          <h1 className="staff-title">{title}</h1><p className="internal-page-intro">{subtitle}</p></div>
        <span className="staff-date">{TODAY_LABEL}</span>
      </div>
      <nav className="staff-tabs" aria-label="Điều hướng nội bộ">
        {items.map((item) => <button key={item.id} type="button" className={`staff-tab${active === item.id ? ' active' : ''}`}
          aria-current={active === item.id ? 'page' : undefined} onClick={() => onNavigate(item.id)}>
          <InternalNavIcon name={item.icon} /><span>{item.label}</span></button>)}
      </nav>
      {children}
    </main>
  </div>
}
