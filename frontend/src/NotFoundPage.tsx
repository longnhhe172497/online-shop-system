import { Link } from 'react-router-dom'
import StoreLayout from './StoreLayout'

export default function NotFoundPage() {
  return <StoreLayout>
    <main className="fs-shell fs-not-found">
      <p className="fs-eyebrow">LỖI 404</p>
      <h1>Trang không tồn tại</h1>
      <p>Đường dẫn bạn vừa mở không còn tồn tại hoặc đã được chuyển sang vị trí khác.</p>
      <div className="fs-not-found-actions">
        <Link className="fs-btn orange" to="/">VỀ TRANG SẢN PHẨM</Link>
        <Link className="fs-back" to="/me">Mở tài khoản của tôi →</Link>
      </div>
    </main>
  </StoreLayout>
}
