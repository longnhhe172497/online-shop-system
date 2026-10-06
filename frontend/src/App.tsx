import { useEffect, useState } from 'react'
import { listProducts, type Product } from './api/products'
import './App.css'

function App() {
  const [products, setProducts] = useState<Product[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')

  useEffect(() => {
    let active = true
    listProducts()
      .then((page) => {
        if (active) {
          setProducts(page.items)
          setStatus('ready')
        }
      })
      .catch(() => {
        if (active) setStatus('error')
      })
    return () => { active = false }
  }, [])

  return (
    <main className="app-shell">
      <section className="status-card">
        <p className="eyebrow">Online Shop System</p>
        <h1>Products</h1>
        {status === 'loading' && <p>Loading products…</p>}
        {status === 'error' && <p role="alert">Cannot load products. Check that the backend and PostgreSQL are running.</p>}
        {status === 'ready' && products.length === 0 && <p>No active products yet. Add one in PostgreSQL to verify the full flow.</p>}
        {status === 'ready' && products.length > 0 && (
          <ul className="product-list">
            {products.map((product) => (
              <li key={product.id}>
                <strong>{product.name}</strong>
                <span>{product.categoryName} · {product.price.toLocaleString('vi-VN')} ₫</span>
                <small>Available: {product.availableQuantity}</small>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

export default App
