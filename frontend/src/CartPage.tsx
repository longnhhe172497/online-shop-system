import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCart, removeCartItem, updateCartItem, type Cart, type CartLine } from './api/cart'
import { getMe, type AuthUser } from './api/auth'
import StoreHeader from './StoreHeader'
import ProductImage from './ProductImage'
import { formatVnd } from './formatMoney'
import { getUiError } from './uiError'

/** Stream C's checkout page. */
const CHECKOUT_PATH = '/checkout'
const MAX_LINE_QUANTITY = 99

function warningText(line: CartLine) {
  if (line.warning === 'PRODUCT_UNAVAILABLE') return 'Sản phẩm này đã ngừng bán. Hãy xóa khỏi giỏ để tiếp tục.'
  if (line.availableQuantity > 0) return `Chỉ còn ${line.availableQuantity} sản phẩm. Hãy giảm số lượng.`
  return 'Sản phẩm đã hết hàng. Hãy xóa khỏi giỏ để tiếp tục.'
}

export default function CartPage() {
  const [cart, setCart] = useState<Cart | null>(null)
  const [loadError, setLoadError] = useState('')
  const [user, setUser] = useState<AuthUser | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    let active = true
    getCart().then((data) => { if (active) setCart(data) })
      .catch((error: unknown) => { if (active) setLoadError(getUiError(error)) })
    getMe().then((me) => { if (active) setUser(me) }).catch(() => {})
    return () => { active = false }
  }, [])

  async function run(lineId: number, action: () => Promise<Cart>) {
    setBusyId(lineId)
    setActionError('')
    try {
      setCart(await action())
      setConfirmId(null)
    } catch (error) {
      setActionError(getUiError(error))
    } finally {
      setBusyId(null)
    }
  }

  const flagged = cart?.items.some((line) => line.warning !== null) ?? false

  return <div className="forme-page">
    <StoreHeader />
    <main className="shop-main">
      <div className="shop-pagehead">
        <div>
          <p className="shop-kicker">LỰA CHỌN CỦA BẠN</p>
          <h1>Giỏ hàng</h1>
        </div>
        {cart && cart.items.length > 0 && <span>{cart.itemCount} sản phẩm</span>}
      </div>

      {!cart && !loadError && <p className="shop-state">Đang tải giỏ hàng…</p>}
      {loadError && <p className="shop-state" role="alert">{loadError}</p>}

      {cart && cart.items.length === 0 && <div className="shop-empty">
        <h3>Giỏ hàng đang trống.</h3>
        <p>Hãy chọn vài sản phẩm bạn thích.</p>
        <Link className="shop-primary" to="/">TIẾP TỤC MUA SẮM</Link>
      </div>}

      {cart && cart.items.length > 0 && <div className="shop-cart">
        <ul className="shop-cart-lines">
          {cart.items.map((line) => {
            const busy = busyId === line.id
            const maxQuantity = Math.min(line.availableQuantity, MAX_LINE_QUANTITY)
            return <li key={line.id} className={line.warning ? 'shop-cart-line flagged' : 'shop-cart-line'}>
              <Link to={`/products/${line.productId}`} tabIndex={-1} aria-hidden="true">
                <ProductImage src={line.imageUrl} alt={line.name} />
              </Link>
              <div className="shop-cart-info">
                <Link to={`/products/${line.productId}`}><strong>{line.name}</strong></Link>
                <small>Đơn giá {formatVnd(line.unitPrice)}</small>
                {line.warning && <p className="shop-cart-warning" role="alert">{warningText(line)}</p>}
                <div className="shop-qty" role="group" aria-label={`Số lượng ${line.name}`}>
                  <button type="button" aria-label="Giảm số lượng" disabled={busy || line.quantity <= 1}
                    onClick={() => run(line.id, () => updateCartItem(line.id, line.quantity - 1))}>−</button>
                  <output>{line.quantity}</output>
                  <button type="button" aria-label="Tăng số lượng"
                    disabled={busy || line.warning !== null || line.quantity >= maxQuantity}
                    onClick={() => run(line.id, () => updateCartItem(line.id, line.quantity + 1))}>+</button>
                </div>
              </div>
              <div className="shop-cart-side">
                <b>{formatVnd(line.lineTotal)}</b>
                {confirmId === line.id
                  ? <div className="shop-confirm" role="group" aria-label="Xác nhận xóa">
                      <span>Xóa sản phẩm này?</span>
                      <button className="shop-secondary" type="button" disabled={busy}
                        onClick={() => run(line.id, () => removeCartItem(line.id))}>Xóa</button>
                      <button className="shop-secondary" type="button" onClick={() => setConfirmId(null)}>Hủy</button>
                    </div>
                  : <button className="shop-link" type="button" onClick={() => setConfirmId(line.id)}>Xóa</button>}
              </div>
            </li>
          })}
        </ul>

        <aside className="shop-summary" aria-label="Tóm tắt giỏ hàng">
          <h2>Tóm tắt</h2>
          <div className="shop-summary-row"><span>Tạm tính</span><b>{formatVnd(cart.subtotal)}</b></div>
          <p className="shop-note">Phí giao hàng và mã giảm giá được áp dụng ở bước thanh toán.</p>
          {actionError && <p className="shop-state" role="alert">{actionError}</p>}
          {flagged && <p className="shop-note">Hãy điều chỉnh các sản phẩm được đánh dấu trước khi thanh toán.</p>}
          {!user
            ? <Link className="shop-primary" to="/auth">ĐĂNG NHẬP ĐỂ THANH TOÁN</Link>
            : flagged
              ? <button className="shop-primary" type="button" disabled>THANH TOÁN</button>
              : <Link className="shop-primary" to={CHECKOUT_PATH}>THANH TOÁN</Link>}
          <Link className="shop-back" to="/">← Tiếp tục mua sắm</Link>
        </aside>
      </div>}
    </main>
    <footer className="forme-footer">
      <strong>FORME</strong>
      <span>Thời trang tối giản cho mỗi ngày.</span>
    </footer>
  </div>
}
