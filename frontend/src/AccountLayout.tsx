import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'

export default function AccountLayout({ title, intro, children }: { title: string; intro: string;
  children: ReactNode }) {
  return <div className="forme-page account-layout">
    <div className="announcement">FORME · KHÔNG GIAN TÀI KHOẢN</div>
    <header className="forme-header"><Link to="/" className="brand">FORME</Link>
      <nav aria-label="Điều hướng chính"><Link to="/">CỬA HÀNG</Link><Link to="/me">TÀI KHOẢN</Link></nav></header>
    <main className="account-workspace"><aside className="account-nav-panel"><p className="auth-kicker">TÀI KHOẢN CỦA TÔI</p>
      <h2>Xin chào.</h2><p>Thông tin cá nhân và bảo mật của bạn.</p>
      <nav aria-label="Điều hướng tài khoản">
        <NavLink to="/me" end>Hồ sơ cá nhân <span>→</span></NavLink>
        <NavLink to="/me/addresses">Địa chỉ giao hàng <span>→</span></NavLink>
        <NavLink to="/me/security">Bảo mật tài khoản <span>→</span></NavLink>
        <NavLink to="/me/email">Đổi email đăng nhập <span>→</span></NavLink>
        <NavLink to="/me/sessions">Thiết bị đăng nhập <span>→</span></NavLink>
      </nav><Link className="account-return" to="/">← Về cửa hàng</Link>
    </aside><div className="account-workspace-content"><div className="account-heading">
      <div><p className="auth-kicker">FORME · TÀI KHOẢN</p><h1>{title}</h1><p>{intro}</p></div>
    </div>{children}</div></main>
    <footer className="forme-footer"><strong>FORME</strong><span>Cửa hàng trực tuyến · Bản chạy trên máy cá nhân</span></footer>
  </div>
}
