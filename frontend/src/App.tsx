import { useEffect, useState } from 'react'
import { Link, Route, Routes } from 'react-router-dom'
import { listProducts, type Product } from './api/products'
import { getMe, logout, type AuthUser } from './api/auth'
import { hasAccessToken } from './api/client'
import AuthPage from './AuthPage'
import VerifyPage from './VerifyPage'
import PasswordResetPage from './PasswordResetPage'
import ProfilePage from './ProfilePage'
import AdminPage from './AdminPage'
import './App.css'

function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [user, setUser] = useState<AuthUser | null>(null)

  useEffect(() => {
    let active = true
    listProducts().then((page) => { if (active) { setProducts(page.items); setStatus('ready') } })
      .catch(() => { if (active) setStatus('error') })
    if (hasAccessToken()) getMe().then((me) => { if (active) setUser(me) }).catch(() => {})
    return () => { active = false }
  }, [])

  async function signOut() {
    await logout().catch(() => {})
    setUser(null)
  }

  return <main className="app-shell"><section className="status-card">
    <div className="shop-topline"><p className="eyebrow">FORME</p>
      {user ? <div className="shop-account">{user.role === 'ADMIN' && <Link to="/admin">Quản trị</Link>}
        <Link to="/me">Hồ sơ của tôi</Link>
        <button onClick={signOut}>Đăng xuất · {user.fullName}</button></div> : <Link to="/auth">Đăng nhập</Link>}
    </div>
    <h1>Sản phẩm</h1>
    {status === 'loading' && <p>Đang tải sản phẩm…</p>}
    {status === 'error' && <p role="alert">Không thể tải sản phẩm. Hãy kiểm tra backend và PostgreSQL đang chạy.</p>}
    {status === 'ready' && products.length === 0 && <p>Chưa có sản phẩm đang bán.</p>}
    {status === 'ready' && products.length > 0 && <ul className="product-list">{products.map((product) => <li key={product.id}>
      <strong>{product.name}</strong><span>{product.categoryName} · {product.price.toLocaleString('vi-VN')} ₫</span>
      <small>Còn hàng: {product.availableQuantity}</small>
    </li>)}</ul>}
  </section></main>
}

export default function App() {
  return <Routes>
    <Route path="/" element={<ProductsPage />} />
    <Route path="/auth" element={<AuthPage />} />
    <Route path="/verify-email" element={<VerifyPage />} />
    <Route path="/forgot-password" element={<PasswordResetPage />} />
    <Route path="/reset-password" element={<PasswordResetPage />} />
    <Route path="/me" element={<ProfilePage />} />
    <Route path="/admin" element={<AdminPage />} />
  </Routes>
}
