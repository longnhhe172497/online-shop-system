import { isAxiosError } from 'axios'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { placeOrder, previewCheckout, type CheckoutPreview, type PaymentMethod } from './api/orders'
import { listAddresses, type Address } from './api/profile'
import { formatVnd, SIMULATION_NOTICE } from './orderFormat'
import { CheckoutSteps, Icon, ProductThumb, ShopLayout } from './shopUi'
import { getUiError } from './uiError'
import './orders.css'

const problemText = {
  PRODUCT_UNAVAILABLE: 'Sản phẩm đã ngừng bán',
  INSUFFICIENT_STOCK: 'Không đủ hàng',
}

function formatAddress(address: Address) {
  return [address.addressLine, address.ward, address.district, address.province].filter(Boolean).join(', ')
}

export default function CheckoutPage() {
  const navigate = useNavigate()
  const [preview, setPreview] = useState<CheckoutPreview | null>(null)
  const [addresses, setAddresses] = useState<Address[]>([])
  const [addressId, setAddressId] = useState<number | null>(null)
  const [choosingAddress, setChoosingAddress] = useState(false)
  const [method, setMethod] = useState<PaymentMethod>('SIMULATED_QR')
  const [voucherInput, setVoucherInput] = useState('')
  const [voucherError, setVoucherError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loadProblem, setLoadProblem] = useState<'login' | 'role' | 'failed' | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setLoadProblem(null); setError('')
    try {
      const [quote, saved] = await Promise.all([previewCheckout(), listAddresses()])
      setPreview(quote)
      setAddresses(saved)
      setAddressId(saved.find((address) => address.isDefault)?.id ?? saved[0]?.id ?? null)
    } catch (failure) {
      const status = isAxiosError(failure) ? failure.response?.status : undefined
      setLoadProblem(status === 401 ? 'login' : status === 403 ? 'role' : 'failed')
    } finally { setLoading(false) }
  }, [])
  useEffect(() => { void Promise.resolve().then(load) }, [load])

  async function applyVoucher(code: string) {
    setVoucherError(''); setBusy(true)
    try { setPreview(await previewCheckout(code.trim())); if (!code.trim()) setVoucherInput('') }
    catch (failure) { setVoucherError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!addressId || !preview) return
    setError(''); setBusy(true)
    try {
      const order = await placeOrder({ addressId, paymentMethod: method, voucherCode: preview.voucherCode ?? undefined })
      navigate(`/me/orders/${order.id}`, { replace: true })
    } catch (failure) {
      setError(getUiError(failure))
      try { setPreview(await previewCheckout(preview.voucherCode ?? undefined)) } catch { /* keep the last quote */ }
      setBusy(false)
    }
  }

  const empty = preview !== null && preview.items.length === 0
  const selected = addresses.find((address) => address.id === addressId) ?? null
  const itemCount = preview?.items.reduce((sum, line) => sum + line.quantity, 0) ?? 0

  return <ShopLayout minimal><div className="checkout-page">
    <CheckoutSteps current={empty ? 1 : 2} />
    <div className="checkout-title"><h1>Thanh toán</h1>
      {preview && !empty && <span>{itemCount} sản phẩm</span>}</div>
    {loading && <div className="checkout-skeleton" role="status">Đang tải giỏ hàng…</div>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    {!loading && loadProblem === 'login' && <section className="shop-card empty-state"><Icon name="lock" size={36} />
      <h2>Bạn cần đăng nhập</h2><p>Đăng nhập bằng tài khoản khách hàng để thanh toán đơn hàng.</p>
      <Link className="shop-button" to="/auth?next=/checkout">Đăng nhập</Link></section>}
    {!loading && loadProblem === 'role' && <section className="shop-card empty-state"><Icon name="lock" size={36} />
      <h2>Chỉ tài khoản khách hàng được đặt hàng</h2>
      <p>Bạn đang đăng nhập bằng tài khoản nhân viên. Hãy đăng xuất và dùng tài khoản khách hàng.</p>
      <Link className="shop-button" to="/">Về cửa hàng</Link></section>}
    {!loading && loadProblem === 'failed' && <section className="shop-card empty-state"><Icon name="x" size={36} />
      <h2>Không tải được giỏ hàng</h2>
      <p>Máy chủ chưa phản hồi đúng. Hãy kiểm tra backend đang chạy bản mới nhất rồi thử lại.</p>
      <button type="button" className="shop-button" onClick={() => void load()}>Thử lại</button></section>}
    {empty && <section className="shop-card empty-state"><Icon name="cart" size={40} />
      <h2>Giỏ hàng đang trống</h2><p>Hãy thêm sản phẩm vào giỏ trước khi thanh toán.</p>
      <Link className="shop-button" to="/">Tiếp tục mua sắm</Link></section>}

    {preview && !empty && <form className="checkout-layout" onSubmit={submit}>
      <div className="checkout-main">
        <section className="shop-card">
          <div className="card-title"><span className="card-icon"><Icon name="pin" /></span><h2>Địa chỉ nhận hàng</h2>
            {addresses.length > 1 && <button type="button" className="link-button"
              onClick={() => setChoosingAddress(!choosingAddress)}>{choosingAddress ? 'Xong' : 'Thay đổi'}</button>}</div>
          {addresses.length === 0 && <div className="notice-box">Bạn chưa lưu địa chỉ nào.{' '}
            <Link to="/me/addresses">Thêm địa chỉ giao hàng</Link> rồi quay lại trang này.</div>}
          {selected && !choosingAddress && <div className="address-display">
            <strong>{selected.recipientName}</strong><span className="muted">{selected.phone}</span>
            {selected.isDefault && <span className="tag">Mặc định</span>}
            <p>{formatAddress(selected)}</p></div>}
          {choosingAddress && <div className="option-list">{addresses.map((address) => <label key={address.id}
            className={`option-card ${addressId === address.id ? 'selected' : ''}`}>
            <input type="radio" name="address" checked={addressId === address.id}
              onChange={() => { setAddressId(address.id); setChoosingAddress(false) }} />
            <span><strong>{address.recipientName} · {address.phone}</strong>{formatAddress(address)}</span>
            {address.isDefault && <span className="tag">Mặc định</span>}
          </label>)}<Link className="add-link" to="/me/addresses">+ Thêm địa chỉ mới</Link></div>}
        </section>

        <section className="shop-card">
          <div className="card-title"><span className="card-icon"><Icon name="box" /></span><h2>Sản phẩm</h2></div>
          <ul className="line-list">{preview.items.map((line) => <li key={line.productId}
            className={line.problem ? 'has-problem' : ''}>
            <ProductThumb name={line.name} sku={line.sku} />
            <div className="line-info"><strong>{line.name}</strong><span className="muted">Mã: {line.sku}</span>
              {line.problem && <span className="problem-badge">{problemText[line.problem]}
                {line.problem === 'INSUFFICIENT_STOCK' && ` · chỉ còn ${line.availableQuantity}`}</span>}</div>
            <div className="line-qty">{formatVnd(line.unitPrice)}<span>× {line.quantity}</span></div>
            <strong className="line-total">{formatVnd(line.lineSubtotal)}</strong>
          </li>)}</ul>
        </section>

        <section className="shop-card">
          <div className="card-title"><span className="card-icon"><Icon name="shield" /></span><h2>Phương thức thanh toán</h2></div>
          <div className="payment-options">
            <label className={`payment-option ${method === 'SIMULATED_QR' ? 'selected' : ''}`}>
              <input type="radio" name="method" checked={method === 'SIMULATED_QR'} onChange={() => setMethod('SIMULATED_QR')} />
              <span className="payment-icon"><Icon name="qr" size={26} /></span>
              <span><strong>Chuyển khoản qua mã QR</strong>Quét mã để thanh toán ngay, đơn được xác nhận tức thì.
                <em className="sim-chip">Mô phỏng</em></span></label>
            <label className={`payment-option ${method === 'COD' ? 'selected' : ''}`}>
              <input type="radio" name="method" checked={method === 'COD'} onChange={() => setMethod('COD')} />
              <span className="payment-icon"><Icon name="cash" size={26} /></span>
              <span><strong>Thanh toán khi nhận hàng (COD)</strong>Trả tiền mặt cho nhân viên giao hàng. Cửa hàng xác nhận đơn trước khi giao.</span></label>
          </div>
          {method === 'SIMULATED_QR' && <p className="sim-note"><Icon name="shield" size={16} /> {SIMULATION_NOTICE}</p>}
        </section>
      </div>

      <aside className="checkout-summary shop-card">
        <h2>Tóm tắt đơn hàng</h2>
        <div className="voucher-field">
          <span className="voucher-icon"><Icon name="ticket" size={18} /></span>
          <input aria-label="Mã giảm giá" placeholder="Nhập mã giảm giá" value={voucherInput}
            onChange={(event) => setVoucherInput(event.target.value)} disabled={busy} />
          <button type="button" disabled={busy || !voucherInput.trim()} onClick={() => applyVoucher(voucherInput)}>Áp dụng</button>
        </div>
        {voucherError && <p className="field-error" role="alert">{voucherError}</p>}
        {preview.voucherCode && <div className="voucher-chip"><Icon name="ticket" size={16} />
          <span>Đang dùng mã <strong>{preview.voucherCode}</strong></span>
          <button type="button" aria-label="Bỏ mã giảm giá" onClick={() => applyVoucher('')} disabled={busy}>
            <Icon name="x" size={14} /></button></div>}
        <dl className="order-totals">
          <div><dt>Tạm tính ({itemCount} sản phẩm)</dt><dd>{formatVnd(preview.subtotal)}</dd></div>
          <div><dt>Phí giao hàng</dt><dd>{formatVnd(preview.shippingFee)}</dd></div>
          {preview.discountAmount > 0 && <div className="discount"><dt>Giảm giá</dt><dd>− {formatVnd(preview.discountAmount)}</dd></div>}
          <div className="grand"><dt>Tổng cộng</dt><dd>{formatVnd(preview.totalAmount)}</dd></div>
        </dl>
        {!preview.canPlaceOrder && <p className="field-error">Có sản phẩm không thể đặt. Hãy điều chỉnh giỏ hàng.</p>}
        {!addressId && <p className="field-error">Hãy thêm địa chỉ nhận hàng.</p>}
        <button type="submit" className="shop-button block" aria-label="Đặt hàng"
          disabled={busy || !preview.canPlaceOrder || !addressId}>
          {busy ? 'Đang xử lý…' : <>Đặt hàng <span>· {formatVnd(preview.totalAmount)}</span></>}</button>
        <p className="fine-print">Nhấn “Đặt hàng” đồng nghĩa bạn đồng ý với điều khoản mua hàng của FORME.</p>
        <ul className="trust-list">
          <li><Icon name="truck" size={18} /> Giao hàng toàn quốc</li>
          <li><Icon name="return" size={18} /> Đổi trả trong 7 ngày</li>
        </ul>
      </aside>
    </form>}
  </div></ShopLayout>
}
