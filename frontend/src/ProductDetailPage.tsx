import axios from 'axios'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getProduct, type ProductDetail } from './api/catalog'
import { addCartItem } from './api/cart'
import StoreHeader from './StoreHeader'
import ProductImage from './ProductImage'
import { formatVnd } from './formatMoney'
import { getUiError } from './uiError'

interface Loaded {
  id: number
  product?: ProductDetail
  error?: 'missing' | 'failed'
}

function Rating({ avg, count }: { avg: number | null; count: number }) {
  if (count === 0 || avg === null) return <p className="shop-rating">Chưa có đánh giá</p>
  const full = Math.min(5, Math.max(0, Math.round(avg)))
  return <p className="shop-rating">
    <span className="shop-stars" aria-hidden="true">{'★'.repeat(full)}{'☆'.repeat(5 - full)}</span>
    <span>{avg.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} / 5 · {count} đánh giá</span>
  </p>
}

export default function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const validId = Number.isInteger(productId) && productId > 0
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null)

  async function addToCart(productId: number, amount: number) {
    setAdding(true)
    setNotice(null)
    try {
      await addCartItem(productId, amount)
      setNotice({ ok: true, text: 'Đã thêm vào giỏ hàng.' })
    } catch (error) {
      setNotice({ ok: false, text: getUiError(error) })
    } finally {
      setAdding(false)
    }
  }

  useEffect(() => {
    if (!validId) return
    let active = true
    getProduct(productId)
      .then((product) => { if (active) setLoaded({ id: productId, product }) })
      .catch((error: unknown) => {
        if (!active) return
        const missing = axios.isAxiosError(error) && error.response?.status === 404
        setLoaded({ id: productId, error: missing ? 'missing' : 'failed' })
      })
    return () => { active = false }
  }, [productId, validId])

  const current = loaded?.id === productId ? loaded : null
  const product = current?.product

  let body
  if (!validId || current?.error === 'missing') {
    body = <div className="shop-empty">
      <h1>Không tìm thấy sản phẩm</h1>
      <p>Sản phẩm không tồn tại hoặc đã ngừng bán.</p>
      <Link className="shop-primary" to="/">XEM SẢN PHẨM KHÁC</Link>
    </div>
  } else if (current?.error === 'failed') {
    body = <p className="shop-state" role="alert">Không thể tải sản phẩm. Vui lòng thử lại sau.</p>
  } else if (!product) {
    body = <p className="shop-state">Đang tải sản phẩm…</p>
  } else {
    const available = Math.max(product.availableQuantity, 0)
    const qty = Math.min(Math.max(quantity, 1), Math.max(available, 1))
    body = <>
      <div className="shop-detail">
        <ProductImage src={product.imageUrl} alt={product.name} />
        <div className="shop-detail-info">
          <p className="shop-kicker">{product.categoryName.toUpperCase()}</p>
          <h1>{product.name}</h1>
          <p className="shop-price">{formatVnd(product.price)}</p>
          <Rating avg={product.avgRating} count={product.reviewCount} />
          {product.description && <p className="shop-description">{product.description}</p>}
          <p className={available > 0 ? 'shop-stock' : 'shop-stock out'}>
            {available > 0 ? `Còn hàng · còn ${available} sản phẩm` : 'Tạm hết hàng'}</p>

          <span className="shop-qty-label" id="shop-qty-label">SỐ LƯỢNG</span>
          <div className="shop-qty" role="group" aria-labelledby="shop-qty-label">
            <button type="button" aria-label="Giảm số lượng" disabled={qty <= 1 || available === 0}
              onClick={() => setQuantity(qty - 1)}>−</button>
            <output aria-live="polite">{qty}</output>
            <button type="button" aria-label="Tăng số lượng" disabled={qty >= available}
              onClick={() => setQuantity(qty + 1)}>+</button>
          </div>

          <button className="shop-primary" type="button" disabled={available === 0 || adding}
            onClick={() => addToCart(product.id, qty)}>{adding ? 'ĐANG THÊM…' : 'THÊM VÀO GIỎ HÀNG'}</button>
          {notice && <p className={notice.ok ? 'shop-notice' : 'shop-notice error'} role={notice.ok ? 'status' : 'alert'}>
            <span>{notice.text}</span>{notice.ok && <Link to="/cart">Xem giỏ hàng</Link>}
          </p>}
          <p className="shop-meta">Mã sản phẩm: {product.sku}</p>
        </div>
      </div>

      <section className="shop-reviews" aria-labelledby="shop-reviews-title">
        <h2 id="shop-reviews-title">Đánh giá</h2>
        <p>{product.reviewCount === 0
          ? 'Chưa có đánh giá nào cho sản phẩm này.'
          : `${product.reviewCount} đánh giá từ khách hàng đã nhận hàng.`}</p>
      </section>
    </>
  }

  return <div className="forme-page">
    <StoreHeader />
    <main className="shop-main">
      <Link className="shop-back" to="/">← Quay lại danh sách sản phẩm</Link>
      {body}
    </main>
    <footer className="forme-footer">
      <strong>FORME</strong>
      <span>Thời trang tối giản cho mỗi ngày.</span>
    </footer>
  </div>
}