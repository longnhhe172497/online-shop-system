import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import StoreHeader from './StoreHeader'
import './storefront.css'

export function StoreFooter() {
  return <footer className="fs-footer">
    <div className="fs-footer-inner">
      <div className="fs-footer-intro"><Link className="fs-footer-brand" to="/">FORME</Link>
        <p>Thời trang tối giản với màu sắc tự tin, thiết kế cho mọi chuyển động thường ngày.</p></div>
      <div><b className="fs-smallcaps">Mua sắm</b>
        <ul><li><Link to="/">Tất cả sản phẩm</Link></li><li><Link to="/cart">Giỏ hàng của tôi</Link></li></ul></div>
      <div><b className="fs-smallcaps">Tài khoản</b>
        <ul><li><Link to="/me/orders">Đơn hàng của tôi</Link></li><li><Link to="/me">Hồ sơ cá nhân</Link></li>
          <li><Link to="/me/support">Trung tâm hỗ trợ</Link></li></ul></div>
    </div>
    <div className="fs-footer-bottom"><span>© 2026 FORME</span><span>Mua sắm an toàn · Đổi trả trong 7 ngày</span></div>
  </footer>
}

/** Page frame for the Stream B storefront screens (mock-up look). */
export default function StoreLayout({ children }: { children: ReactNode }) {
  return <div className="fs">
    <StoreHeader />
    {children}
    <StoreFooter />
  </div>
}
