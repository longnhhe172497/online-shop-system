import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import SignOutButton from './SignOutButton'
import './logistics.css'

export type InternalIconName = 'dashboard' | 'users' | 'settings' | 'audit' | 'tag'
export interface InternalNavItem<T extends string> {
  id: T
  label: string
  icon: InternalIconName
}

const TODAY_LABEL = new Date().toLocaleDateString('vi-VN', {
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
})

export default function InternalLayout<T extends string>({ items, active, onNavigate, title, subtitle,
  roleName, children }: { items: InternalNavItem<T>[]; active: T; onNavigate: (id: T) => void;
  roleName: string; title: string; subtitle: string; children: ReactNode }) {
  return <div className="staff-root internal-staff-root">
    <header className="staff-header">
      <div className="staff-nav">
        <Link className="staff-brand" to="/">FORME / NỘI BỘ</Link>
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
          aria-current={active === item.id ? 'page' : undefined} onClick={() => onNavigate(item.id)}>{item.label}</button>)}
      </nav>
      {children}
    </main>
  </div>
}
