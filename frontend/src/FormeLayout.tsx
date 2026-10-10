import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

export default function FormeLayout({ children, showcase = false }: { children: ReactNode; showcase?: boolean }) {
  return <div className="forme-page">
    <div className="announcement">FORME · MUA SẮM THUẬN TIỆN, QUẢN LÝ AN TOÀN</div>
    <header className="forme-header"><Link to="/" className="brand">FORME</Link>
      <nav aria-label="Điều hướng chính"><Link to="/">CỬA HÀNG</Link><Link to="/auth">ĐĂNG NHẬP</Link></nav>
    </header>
    <main className={showcase ? 'auth-main auth-main-split' : 'auth-main'}>
      {showcase && <aside className="auth-showcase" aria-label="Giới thiệu FORME">
        <span className="auth-showcase-mark">F</span>
        <p className="auth-kicker">KHÔNG GIAN CỦA BẠN</p>
        <h2>Mọi trải nghiệm bắt đầu từ một tài khoản an toàn.</h2>
        <p>Đăng nhập để quản lý đơn hàng, địa chỉ và thông tin cá nhân trong một không gian gọn gàng.</p>
        <div className="auth-showcase-foot"><span>01 / 03</span><span>FORME · TÀI KHOẢN</span></div>
      </aside>}
      {children}
    </main>
    <footer className="forme-footer"><strong>FORME</strong><span>Cửa hàng trực tuyến · Bản chạy trên máy cá nhân</span></footer>
  </div>
}
