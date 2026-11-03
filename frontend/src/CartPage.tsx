import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCart, removeCartItem, updateCartItem, type Cart, type CartLine } from './api/cart'
import { getMe, type AuthUser } from './api/auth'
import StoreLayout from './StoreLayout'
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

  return <StoreLayout>
    <main className="fs-shell">
      <div className="fs-pagehead">
        <div>
          <p className="fs-eyebrow">Lựa chọn của bạn</p>
          <h1>Giỏ hàng</h1>
        </div>
        {cart && cart.items.length > 0 && <span className="fs-muted">{cart.itemCount} sản phẩm</span>}
      </div>

      {!cart && !loadError && <p className="fs-state">Đang tải giỏ hàng…</p>}
      {loadError && <p className="fs-state" role="alert">{loadError}</p>}

      {cart && cart.items.length === 0 && <div className="fs-empty">
        <h3>Giỏ hàng đang trống.</h3>
        <p>Hãy chọn vài sản phẩm bạn thích.</p>
        <Link className="fs-btn" to="/">Tiếp tục mua sắm</Link>
      </div>}

      {cart && cart.items.length > 0 && <div className="fs-aside-layout">
        <ul className="fs-cart-lines">
          {cart.items.map((line) => {
            const busy = busyId === line.id
            const maxQuantity = Math.min(line.availableQuantity, MAX_LINE_QUANTITY)
            return <li key={line.id} className={line.warning ? 'fs-card fs-cart-line flagged' : 'fs-card fs-cart-line'}>
              <Link to={`/products/${line.productId}`} tabIndex={-1} aria-hidden="true">
                <ProductImage src={line.imageUrl} alt={line.name} />
              </Link>
              <div className="fs-cart-info">
                <h3><Link to={`/products/${line.productId}`}>{line.name}</Link></h3>
                <p className="fs-muted">Đơn giá {formatVnd(line.unitPrice)}</p>
                {line.warning && <p className="fs-cart-warning" role="alert">{warningText(line)}</p>}
                <div className="fs-qty" role="group" aria-label={`Số lượng ${line.name}`}>
                  <button type="button" aria-label="Giảm số lượng" disabled={busy || line.quantity <= 1}
                    onClick={() => run(line.id, () => updateCartItem(line.id, line.quantity - 1))}>−</button>
                  <output>{line.quantity}</output>
                  <button type="button" aria-label="Tăng số lượng"
                    disabled={busy || line.warning !== null || line.quantity >= maxQuantity}
                    onClick={() => run(line.id, () => updateCartItem(line.id, line.quantity + 1))}>+</button>
                </div>
              </div>
              <div className="fs-cart-side">
                <b>{formatVnd(line.lineTotal)}</b>
                {confirmId === line.id
                  ? <div className="fs-confirm" role="group" aria-label="Xác nhận xóa">
                      <span>Xóa sản phẩm này?</span>
                      <button className="fs-btn sm" type="button" disabled={busy}
                        onClick={() => run(line.id, () => removeCartItem(line.id))}>Xóa</button>
                      <button className="fs-btn ghost sm" type="button" onClick={() => setConfirmId(null)}>Hủy</button>
                    </div>
                  : <button className="fs-btn ghost sm" type="button" onClick={() => setConfirmId(line.id)}>Xóa</button>}
              </div>
            </li>
          })}
        </ul>

        <aside className="fs-panel fs-summary" aria-label="Tóm tắt giỏ hàng">
          <h2>Tóm tắt</h2>
          <div className="fs-summary-row"><span>Tạm tính</span><b>{formatVnd(cart.subtotal)}</b></div>
          <div className="fs-summary-row"><span>Phí giao hàng</span><span className="fs-muted">Tính khi thanh toán</span></div>
          <p className="fs-muted">Mã giảm giá được áp dụng ở bước thanh toán.</p>
          {actionError && <p className="fs-state" role="alert">{actionError}</p>}
          {flagged && <p className="fs-cart-warning">Hãy điều chỉnh các sản phẩm được đánh dấu trước khi thanh toán.</p>}
          {!user
            ? <Link className="fs-btn orange block" to={`/auth?next=${CHECKOUT_PATH}`}>ĐĂNG NHẬP ĐỂ THANH TOÁN</Link>
            : flagged
              ? <button className="fs-btn orange block" type="button" disabled>THANH TOÁN</button>
              : <Link className="fs-btn orange block" to={CHECKOUT_PATH}>THANH TOÁN</Link>}
          <Link className="fs-back" to="/">← Tiếp tục mua sắm</Link>
        </aside>
      </div>}
    </main>
  </StoreLayout>
}
