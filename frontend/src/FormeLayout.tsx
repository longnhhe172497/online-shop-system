import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

export function FormeHeader() {
  return <>
    <div className="forme-demo">CỬA HÀNG TRỰC TUYẾN · PHIÊN BẢN THỬ NGHIỆM</div>
    <div className="announcement">MIỄN PHÍ GIAO HÀNG CHO ĐƠN TỪ 1.500.000 ₫</div>
    <header className="forme-header">
      <nav className="forme-nav" aria-label="Điều hướng chính">
        <Link to="/" className="brand" aria-label="FORME - Trang chủ">FORME</Link>
        <div className="forme-nav-links">
          <Link to="/">CỬA HÀNG</Link>
          <Link to="/me">TÀI KHOẢN CỦA TÔI</Link>
        </div>
        <div className="forme-nav-actions">
          <Link to="/auth" className="forme-icon-link" aria-label="Đăng nhập hoặc mở tài khoản">
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4" />
              <path d="M4 21a8 8 0 0 1 16 0" /></svg>
          </Link>
          <Link to="/checkout" className="forme-icon-link" aria-label="Mở giỏ hàng">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 8h12l1 13H5L6 8Z" />
              <path d="M9 9V6a3 3 0 0 1 6 0v3" /></svg>
          </Link>
        </div>
      </nav>
    </header>
  </>
}

export function FormeFooter() {
  return <footer className="forme-footer">
      <div className="forme-footer-inner">
        <div className="forme-footer-brand"><strong>FORME</strong>
          <p>Thời trang được cân nhắc kỹ trong những gam màu tự tin, dành cho chuyển động mỗi ngày.</p></div>
        <div><b>CỬA HÀNG</b><Link to="/">Sản phẩm mới</Link><Link to="/">Trang phục</Link>
          <Link to="/">Khuyến mãi</Link></div>
        <div><b>TÀI KHOẢN</b><Link to="/me">Hồ sơ</Link><Link to="/me/addresses">Địa chỉ giao hàng</Link>
          <Link to="/me/security">Bảo mật</Link></div>
        <div className="forme-newsletter"><b>BẢN TIN MÀU SẮC</b>
          <p>Nhận tin mới, gợi ý phối đồ và quyền truy cập sớm.</p>
          <div className="forme-newsletter-form"><label className="sr-only" htmlFor="forme-newsletter-email">Địa chỉ email nhận bản tin</label>
            <input id="forme-newsletter-email" type="email" disabled placeholder="Địa chỉ email" />
            <button type="button" disabled>ĐĂNG KÝ</button></div>
          <span>Tính năng bản tin sẽ được cập nhật sau.</span>
        </div>
      </div>
    </footer>
}

export default function FormeLayout({ children, showcase = false }: { children: ReactNode; showcase?: boolean }) {
  return <div className="forme-page">
    <FormeHeader />
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
    <FormeFooter />
  </div>
}
