import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import SignOutButton from './SignOutButton'
import './logistics.css'

export interface StaffTab<T extends string> {
  id: T
  label: string
}

interface StaffLayoutProps<T extends string> {
  eyebrow: string
  title: string
  subtitle?: string
  roleName: string
  tabs: StaffTab<T>[]
  active: T
  onNavigate: (id: T) => void
  children: ReactNode
}

const TODAY_LABEL = new Date().toLocaleDateString('vi-VN', {
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
})

export default function StaffLayout<T extends string>({
  eyebrow, title, subtitle, roleName, tabs, active, onNavigate, children,
}: StaffLayoutProps<T>) {
  return <div className="staff-root">
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
          <Link className="staff-iconbtn" to="/me" aria-label="Hồ sơ của tôi">
            <svg className="staff-icon" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="8" r="4" /><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1" />
            </svg>
          </Link>
          <SignOutButton className="staff-signout" />
        </div>
      </div>
    </header>
    <main className="staff-shell">
      <div className="staff-pagehead">
        <div><div className="staff-eyebrow">{eyebrow}</div><h1 className="staff-title">{title}</h1>
          {subtitle && <p className="staff-page-intro">{subtitle}</p>}</div>
        <span className="staff-date">{TODAY_LABEL}</span>
      </div>
      <div className="staff-tabs" role="tablist">
        {tabs.map((tab) => <button key={tab.id} type="button" role="tab" aria-selected={active === tab.id}
          className={`staff-tab${active === tab.id ? ' active' : ''}`} onClick={() => onNavigate(tab.id)}>
          {tab.label}</button>)}
      </div>
      {children}
    </main>
  </div>
}
