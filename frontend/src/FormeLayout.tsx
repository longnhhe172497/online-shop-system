import type { ReactNode } from 'react'
import StoreHeader from './StoreHeader'
import { StoreFooter } from './StoreLayout'

export function FormeHeader() {
  return <StoreHeader />
}

export function FormeFooter() {
  return <StoreFooter />
}

export default function FormeLayout({ children, showcase = false }: { children: ReactNode; showcase?: boolean }) {
  return <div className="fs forme-page auth-layout">
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
