import axios from 'axios'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getProduct, type ProductDetail } from './api/catalog'
import { addCartItem } from './api/cart'
import StoreLayout from './StoreLayout'
import ProductImage from './ProductImage'
import { formatVnd } from './formatMoney'
import { getUiError } from './uiError'

interface Loaded {
  id: number
  product?: ProductDetail
  error?: 'missing' | 'failed'
}

function Rating({ avg, count }: { avg: number | null; count: number }) {
  if (count === 0 || avg === null) return <p className="fs-rating">Chưa có đánh giá</p>
  const full = Math.min(5, Math.max(0, Math.round(avg)))
  return <p className="fs-rating">
    <span className="fs-stars" aria-hidden="true">{'★'.repeat(full)}{'☆'.repeat(5 - full)}</span>
    <span>{avg.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} / 5 · {count} đánh giá</span>
  </p>
}

export default function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const validId = Number.isInteger(productId) && productId > 0
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [adding, setAdding] = useState<'cart' | 'buy' | null>(null)
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null)
  const navigate = useNavigate()

  /** "Mua ngay" puts the item in the cart and opens checkout, which asks guests to sign in first. */
  async function addToCart(productId: number, amount: number, buyNow = false) {
    setAdding(buyNow ? 'buy' : 'cart')
    setNotice(null)
    try {
      await addCartItem(productId, amount)
      if (buyNow) {
        navigate('/checkout')
        return
      }
      setNotice({ ok: true, text: 'Đã thêm vào giỏ hàng.' })
    } catch (error) {
      setNotice({ ok: false, text: getUiError(error) })
    }
    setAdding(null)
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
    body = <div className="fs-empty">
      <h1>Không tìm thấy sản phẩm</h1>
      <p>Sản phẩm không tồn tại hoặc đã ngừng bán.</p>
      <Link className="fs-btn" to="/">Xem sản phẩm khác</Link>
    </div>
  } else if (current?.error === 'failed') {
    body = <p className="fs-state" role="alert">Không thể tải sản phẩm. Vui lòng thử lại sau.</p>
  } else if (!product) {
    body = <p className="fs-state">Đang tải sản phẩm…</p>
  } else {
    const available = Math.max(product.availableQuantity, 0)
    const qty = Math.min(Math.max(quantity, 1), Math.max(available, 1))
    body = <>
      <div className="fs-detail">
        <ProductImage src={product.imageUrl} alt={product.name} />
        <div className="fs-detail-info">
          <p className="fs-eyebrow">{product.categoryName}</p>
          <h1>{product.name}</h1>
          <h3>{formatVnd(product.price)}</h3>
          <Rating avg={product.avgRating} count={product.reviewCount} />
          {product.description && <p className="fs-description">{product.description}</p>}

          <span className="fs-label" id="fs-qty-label">SỐ LƯỢNG</span>
          <div className="fs-qty" role="group" aria-labelledby="fs-qty-label">
            <button type="button" aria-label="Giảm số lượng" disabled={qty <= 1 || available === 0}
              onClick={() => setQuantity(qty - 1)}>−</button>
            <output aria-live="polite">{qty}</output>
            <button type="button" aria-label="Tăng số lượng" disabled={qty >= available}
              onClick={() => setQuantity(qty + 1)}>+</button>
          </div>

          <div className="fs-buy-actions">
            <button className="fs-btn alt" type="button" disabled={available === 0 || adding !== null}
              onClick={() => addToCart(product.id, qty)}>{adding === 'cart' ? 'ĐANG THÊM…' : 'THÊM VÀO GIỎ HÀNG'}</button>
            <button className="fs-btn orange" type="button" disabled={available === 0 || adding !== null}
              onClick={() => addToCart(product.id, qty, true)}>{adding === 'buy' ? 'ĐANG XỬ LÝ…' : 'MUA NGAY'}</button>
          </div>
          {notice && <p className={notice.ok ? 'fs-notice ok' : 'fs-notice error'} role={notice.ok ? 'status' : 'alert'}>
            <span>{notice.text}</span>{notice.ok && <Link to="/cart">Xem giỏ hàng</Link>}
          </p>}
          <p className={available > 0 ? 'fs-notice ok' : 'fs-notice error'}>
            <span aria-hidden="true">{available > 0 ? '✓ ' : '✕ '}</span>
            <span>{available > 0 ? `Còn hàng · còn ${available} sản phẩm` : 'Tạm hết hàng'}</span></p>
          <div className="fs-panel">
            <b className="fs-smallcaps">Thông tin sản phẩm</b>
            <p>Mã sản phẩm: {product.sku} · Danh mục: {product.categoryName}</p>
          </div>
        </div>
      </div>

      <section className="fs-reviews" aria-labelledby="fs-reviews-title">
        <div className="fs-pagehead">
          <div><p className="fs-eyebrow">Ý kiến khách hàng</p><h2 id="fs-reviews-title">Đánh giá</h2></div>
          {product.reviewCount > 0 && product.avgRating !== null && <span>
            <span className="fs-stars" aria-hidden="true">{'★'.repeat(Math.round(product.avgRating))}</span>
            {' '}{product.avgRating.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} ({product.reviewCount})</span>}
        </div>
        <p className="fs-muted">{product.reviewCount === 0
          ? 'Chưa có đánh giá nào cho sản phẩm này.'
          : `${product.reviewCount} đánh giá từ khách hàng đã nhận hàng.`}</p>
      </section>
    </>
  }

  return <StoreLayout>
    <main className="fs-shell">
      <Link className="fs-back" to="/">← Quay lại danh sách sản phẩm</Link>
      {body}
    </main>
  </StoreLayout>
}