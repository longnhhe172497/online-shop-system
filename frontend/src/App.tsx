import { useEffect, useState } from 'react'
import { Link, Route, Routes } from 'react-router-dom'
import { listProducts, type Product } from './api/products'
import { getMe, logout, type AuthUser } from './api/auth'
import { hasAccessToken } from './api/client'
import AuthPage from './AuthPage'
import VerifyPage from './VerifyPage'
import PasswordResetPage from './PasswordResetPage'
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
    <div className="shop-topline"><p className="eyebrow">Online Shop System</p>
      {user ? <button onClick={signOut}>Sign out {user.fullName}</button> : <Link to="/auth">Sign in</Link>}
    </div>
    <h1>Products</h1>
    {status === 'loading' && <p>Loading products…</p>}
    {status === 'error' && <p role="alert">Cannot load products. Check that the backend and PostgreSQL are running.</p>}
    {status === 'ready' && products.length === 0 && <p>No active products yet. Add one in PostgreSQL to verify the full flow.</p>}
    {status === 'ready' && products.length > 0 && <ul className="product-list">{products.map((product) => <li key={product.id}>
      <strong>{product.name}</strong><span>{product.categoryName} · {product.price.toLocaleString('vi-VN')} ₫</span>
      <small>Available: {product.availableQuantity}</small>
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
  </Routes>
}
