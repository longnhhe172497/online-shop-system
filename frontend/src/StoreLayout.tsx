import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import StoreHeader from './StoreHeader'
import './storeFonts'
import './storefront.css'

/** Page frame for the Stream B storefront screens (mock-up look). */
export default function StoreLayout({ children }: { children: ReactNode }) {
  return <div className="fs">
    <StoreHeader />
    {children}
    <footer className="fs-footer">
      <div className="fs-footer-inner">
        <div><h2>Forme</h2>
          <p>Thời trang tối giản với màu sắc tự tin, thiết kế cho mọi chuyển động thường ngày.</p></div>
        <div><b className="fs-smallcaps">Mua sắm</b>
          <ul><li><Link to="/">Tất cả sản phẩm</Link></li><li><Link to="/cart">Giỏ hàng của tôi</Link></li></ul></div>
        <div><b className="fs-smallcaps">Tài khoản</b>
          <ul><li><Link to="/me/orders">Đơn hàng của tôi</Link></li><li><Link to="/me">Hồ sơ cá nhân</Link></li></ul></div>
      </div>
    </footer>
  </div>
}
