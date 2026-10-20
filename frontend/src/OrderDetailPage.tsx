import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { cancelOrder, getOrder, retryPayment, simulatePayment, type OrderDetail } from './api/orders'
import { formatDateTime, formatVnd, orderStatusNames, paymentMethodNames, paymentStatusNames,
  SIMULATION_NOTICE, statusTone } from './orderFormat'
import { Icon, ProductThumb, ShopLayout, type IconName } from './shopUi'
import { getUiError } from './uiError'
import './orders.css'

/** A decorative pattern derived from the simulation code. It is not a real, scannable payment QR. */
function SimulatedQr({ code }: { code: string }) {
  let seed = [...code].reduce((hash, char) => (hash * 31 + char.charCodeAt(0)) >>> 0, 7)
  const cells: boolean[] = []
  for (let i = 0; i < 25 * 25; i++) { seed = (seed * 1103515245 + 12345) >>> 0; cells.push(((seed >>> 16) & 1) === 1) }
  const corners = [[0, 0], [18, 0], [0, 18]]
  const inFinder = (x: number, y: number) => corners.find(([fx, fy]) => x >= fx && x < fx + 7 && y >= fy && y < fy + 7)
  return <svg className="simulated-qr" viewBox="-2 -2 29 29" role="img" aria-label="Mã QR mô phỏng">
    <rect x="-2" y="-2" width="29" height="29" fill="#fff" />
    {cells.map((on, index) => {
      const x = index % 25, y = Math.floor(index / 25)
      const corner = inFinder(x, y)
      let filled = on
      if (corner) {
        const dx = x - corner[0], dy = y - corner[1]
        filled = dx === 0 || dy === 0 || dx === 6 || dy === 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4)
      } else if (x > 9 && x < 15 && y > 9 && y < 15) filled = false
      return filled ? <rect key={index} x={x} y={y} width="1.02" height="1.02" fill="#141521" /> : null
    })}
    <rect x="10.3" y="10.3" width="4.4" height="4.4" rx="1" fill="#2438c5" />
    <text x="12.5" y="13.4" textAnchor="middle" fontSize="2.6" fontWeight="700" fill="#fff" fontFamily="Georgia, serif">F</text>
  </svg>
}

function useSecondsLeft(expiresAt: string | null) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!expiresAt) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [expiresAt])
  return expiresAt ? Math.max(0, Math.floor((new Date(expiresAt).getTime() - now) / 1000)) : 0
}

const trackerSteps: { label: string; icon: IconName }[] = [
  { label: 'Đặt hàng', icon: 'bag' }, { label: 'Xác nhận', icon: 'check' }, { label: 'Đóng gói', icon: 'box' },
  { label: 'Đang giao', icon: 'truck' }, { label: 'Đã giao', icon: 'pin' },
]
const trackerIndex: Record<OrderDetail['status'], number> = {
  AWAITING_PAYMENT: 0, PENDING_CONFIRMATION: 0, CONFIRMED: 1, READY_FOR_DELIVERY: 2, ASSIGNED: 3, DELIVERING: 3,
  DELIVERED: 4, DELIVERY_FAILED: 3, RETURNED_TO_WAREHOUSE: 3, CANCELLED: 0,
}

function hero(order: OrderDetail): { title: string; text: string; tone: string } {
  switch (order.status) {
    case 'AWAITING_PAYMENT': return { title: 'Hoàn tất thanh toán để giữ đơn hàng',
      text: 'Hàng đang được giữ cho bạn trong thời gian mã QR còn hiệu lực.', tone: 'warning' }
    case 'PENDING_CONFIRMATION': return { title: 'Đặt hàng thành công!',
      text: 'Cửa hàng sẽ xác nhận đơn trước khi giao. Bạn thanh toán bằng tiền mặt khi nhận hàng.', tone: 'success' }
    case 'CONFIRMED': return { title: 'Đơn hàng đã được xác nhận',
      text: 'Kho đang chuẩn bị hàng. Bạn vẫn có thể hủy đơn trước khi hàng được đóng gói.', tone: 'success' }
    case 'CANCELLED': return { title: 'Đơn hàng đã hủy',
      text: 'Hàng và mã giảm giá đã được trả lại.', tone: 'danger' }
    case 'DELIVERED': return { title: 'Giao hàng thành công', text: 'Cảm ơn bạn đã mua sắm tại FORME.', tone: 'success' }
    default: return { title: orderStatusNames[order.status], text: 'Theo dõi tiến trình đơn hàng bên dưới.', tone: 'info' }
  }
}

export default function OrderDetailPage() {
  const id = Number(useParams().id)
  const [order, setOrder] = useState<OrderDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [copied, setCopied] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [reason, setReason] = useState('')
  // Length of the current QR window, captured the first time a code is shown; drives the progress bar.
  const [qrWindow, setQrWindow] = useState<{ expiresAt: string | null; seconds: number }>({ expiresAt: null, seconds: 0 })

  useEffect(() => {
    let active = true
    getOrder(id).then((data) => { if (active) setOrder(data) })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id])

  const payment = order?.payment ?? null
  const qrPending = payment?.method === 'SIMULATED_QR' && payment.status === 'PENDING'
  const secondsLeft = useSecondsLeft(qrPending ? payment.expiresAt : null)
  if (qrPending && secondsLeft > 0 && qrWindow.expiresAt !== payment.expiresAt)
    setQrWindow({ expiresAt: payment.expiresAt, seconds: secondsLeft })
  const qrLive = qrPending && secondsLeft > 0
  const canRetry = !!order && (order.paymentRetryAllowed
    || (qrPending && !qrLive && order.paymentAttemptsUsed < order.maxPaymentAttempts))

  async function run(action: () => Promise<OrderDetail>, message: string) {
    setBusy(true); setError(''); setNotice('')
    try { setOrder(await action()); setNotice(message) }
    catch (failure) {
      setError(getUiError(failure))
      try { setOrder(await getOrder(id)) } catch { /* keep the current view */ }
    } finally { setBusy(false) }
  }

  async function copyCode(code: string) {
    try { await navigator.clipboard.writeText(code); setCopied(true); window.setTimeout(() => setCopied(false), 1500) }
    catch { /* clipboard unavailable */ }
  }

  if (loading) return <ShopLayout><div className="order-page"><div className="checkout-skeleton" role="status">Đang tải đơn hàng…</div></div></ShopLayout>
  if (!order) return <ShopLayout><div className="order-page"><section className="shop-card empty-state">
    <Icon name="bag" size={36} /><h2>Không tìm thấy đơn hàng</h2><p role="alert">{error || 'Đơn hàng không tồn tại.'}</p>
    <Link className="shop-button" to="/me/orders">Về danh sách đơn</Link></section></div></ShopLayout>

  const banner = hero(order)
  const step = trackerIndex[order.status]
  const failedDelivery = order.status === 'DELIVERY_FAILED' || order.status === 'RETURNED_TO_WAREHOUSE'
  const progress = qrWindow.seconds ? Math.min(100, (secondsLeft / qrWindow.seconds) * 100) : 0

  return <ShopLayout><div className="order-page">
    <nav className="breadcrumb" aria-label="Vị trí"><Link to="/me">Tài khoản</Link><span>/</span>
      <Link to="/me/orders">Đơn hàng</Link><span>/</span><span>{order.orderCode}</span></nav>

    <section className={`order-hero ${banner.tone}`}>
      <div className="hero-icon"><Icon name={order.status === 'CANCELLED' ? 'x' : order.status === 'AWAITING_PAYMENT' ? 'clock' : 'check'} size={26} /></div>
      <div><h1>{banner.title}</h1><p>{banner.text}</p></div>
      <div className="hero-meta"><span>Mã đơn</span><strong>{order.orderCode}</strong><span>{formatDateTime(order.createdAt)}</span></div>
    </section>
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}

    {order.status !== 'CANCELLED' && <ol className={`order-tracker ${failedDelivery ? 'failed' : ''}`} aria-label="Tiến trình đơn hàng">
      {trackerSteps.map((item, index) => <li key={item.label}
        className={index < step ? 'done' : index === step ? 'current' : ''}>
        <span className="tracker-dot"><Icon name={index < step ? 'check' : item.icon} size={18} /></span>
        <span className="tracker-label">{index === 3 && failedDelivery ? orderStatusNames[order.status] : item.label}</span>
        {index === 0 && <small>{formatDateTime(order.createdAt)}</small>}
        {index === 1 && order.confirmedAt && <small>{formatDateTime(order.confirmedAt)}</small>}
        {index === 4 && order.deliveredAt && <small>{formatDateTime(order.deliveredAt)}</small>}
      </li>)}
    </ol>}

    {order.paymentMethod === 'SIMULATED_QR' && order.status === 'AWAITING_PAYMENT' && <section className="pay-card">
      {qrLive && payment ? <div className="pay-grid">
        <div className="qr-frame"><div className="qr-frame-head"><strong>FORME PAY</strong><span className="sim-chip">Mô phỏng</span></div>
          <SimulatedQr code={payment.simulationCode ?? ''} />
          <p>Quét bằng ứng dụng ngân hàng</p></div>
        <div className="pay-info">
          <p className="card-eyebrow">SỐ TIỀN CẦN THANH TOÁN</p>
          <p className="pay-amount">{formatVnd(payment.amount)}</p>
          <dl className="pay-rows">
            <div><dt>Người nhận</dt><dd>FORME</dd></div>
            <div><dt>Nội dung</dt><dd><code>{payment.simulationCode}</code>
              <button type="button" className="copy-button" onClick={() => void copyCode(payment.simulationCode ?? '')}>
                {copied ? 'Đã chép' : 'Sao chép'}</button></dd></div>
            <div><dt>Lượt thanh toán</dt><dd>{order.paymentAttemptsUsed}/{order.maxPaymentAttempts}</dd></div>
          </dl>
          <div className="countdown"><div><Icon name="clock" size={16} /> Mã hết hạn sau
            <strong> {Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, '0')}</strong></div>
            <div className="countdown-bar"><span style={{ width: `${progress}%` }} /></div></div>
          <div className="sim-panel" role="note"><p><strong>Bảng điều khiển mô phỏng</strong> · {SIMULATION_NOTICE}</p>
            <div className="sim-actions">
              <button type="button" className="shop-button" disabled={busy}
                onClick={() => run(() => simulatePayment(payment.id, 'SUCCESS'), 'Thanh toán mô phỏng thành công. Đơn hàng đã được xác nhận.')}>
                Mô phỏng thanh toán thành công</button>
              <button type="button" className="shop-button ghost" disabled={busy}
                onClick={() => run(() => simulatePayment(payment.id, 'FAILURE'), 'Thanh toán mô phỏng thất bại. Hàng đã được trả lại kho.')}>
                Mô phỏng thanh toán thất bại</button>
            </div></div>
        </div>
      </div> : <div className="pay-retry"><span className="hero-icon warning"><Icon name="clock" size={24} /></span>
        <div><h2>{qrPending ? 'Mã QR đã hết hạn' : 'Thanh toán chưa thành công'}</h2>
          <p>Hàng giữ cho đơn đã được trả lại kho. Khi tạo mã mới, hệ thống kiểm tra lại giá và tồn kho.</p></div>
        {canRetry ? <button type="button" className="shop-button" disabled={busy}
          onClick={() => run(() => retryPayment(order.id), 'Đã tạo mã QR mới.')}>
          Tạo mã QR mới (lượt {order.paymentAttemptsUsed + 1}/{order.maxPaymentAttempts})</button>
          : <p className="muted">Đã dùng hết số lần thanh toán QR.</p>}
      </div>}
    </section>}

    {order.refund && <section className="refund-banner"><Icon name="return" />
      <p><strong>Hoàn tiền mô phỏng {formatVnd(order.refund.amount)}</strong> — {order.refund.status === 'COMPLETED'
        ? 'đã hoàn tất' : 'đang chờ cửa hàng xử lý'}. Không có tiền thật được chuyển.</p></section>}

    <div className="order-columns">
      <section className="shop-card">
        <div className="card-title"><span className="card-icon"><Icon name="box" /></span><h2>Sản phẩm ({order.items.length})</h2></div>
        <ul className="line-list">{order.items.map((item) => <li key={item.id}>
          <ProductThumb name={item.name} sku={item.sku} />
          <div className="line-info"><strong>{item.name}</strong><span className="muted">Mã: {item.sku}</span>
            {item.discountAmount > 0 && <span className="discount-note">Giảm {formatVnd(item.discountAmount)}</span>}</div>
          <div className="line-qty">{formatVnd(item.unitPrice)}<span>× {item.quantity}</span></div>
          <strong className="line-total">{formatVnd(item.lineTotal)}</strong>
        </li>)}</ul>
        <dl className="order-totals">
          <div><dt>Tạm tính</dt><dd>{formatVnd(order.subtotal)}</dd></div>
          <div><dt>Phí giao hàng</dt><dd>{formatVnd(order.shippingFee)}</dd></div>
          {order.discountAmount > 0 && <div className="discount"><dt>Giảm giá{order.voucherCode && ` (${order.voucherCode})`}</dt>
            <dd>− {formatVnd(order.discountAmount)}</dd></div>}
          <div className="grand"><dt>Tổng cộng</dt><dd>{formatVnd(order.totalAmount)}</dd></div>
        </dl>
      </section>

      <aside className="order-side">
        <section className="shop-card"><div className="card-title"><span className="card-icon"><Icon name="pin" /></span>
          <h2>Giao đến</h2></div>
          <p className="side-text"><strong>{order.recipientName}</strong> · {order.recipientPhone}<br />{order.shippingAddress}</p></section>
        <section className="shop-card"><div className="card-title"><span className="card-icon"><Icon name="cash" /></span>
          <h2>Thanh toán</h2></div>
          <p className="side-text">{paymentMethodNames[order.paymentMethod]}</p>
          {payment && <span className={`status-badge ${payment.status === 'PAID' ? 'success' : payment.status === 'PENDING'
            || payment.status === 'UNPAID' ? 'warning' : 'neutral'}`}>{paymentStatusNames[payment.status]}</span>}
          <p className="side-text muted">Trạng thái đơn: <span className={`status-badge ${statusTone(order.status)}`}>
            {orderStatusNames[order.status]}</span></p></section>
        <section className="shop-card help-card"><h2>Cần hỗ trợ?</h2>
          <p className="side-text muted">Bạn có thể hủy đơn trước khi kho đóng gói hàng.</p>
          {order.cancellable && <button type="button" className="shop-button ghost danger block" disabled={busy}
            onClick={() => setCancelOpen(true)}>Hủy đơn</button>}
          <Link className="shop-button ghost block" to="/">Tiếp tục mua sắm</Link></section>
      </aside>
    </div>

    {cancelOpen && <div className="address-modal-backdrop"><section className="address-modal confirm-modal"
      role="dialog" aria-modal="true" aria-labelledby="cancel-order-title">
      <p className="card-eyebrow">XÁC NHẬN</p><h2 id="cancel-order-title">Hủy đơn {order.orderCode}?</h2>
      <p>Hàng và mã giảm giá sẽ được trả lại.{payment?.status === 'PAID'
        && ' Đơn đã thanh toán nên cửa hàng sẽ tạo yêu cầu hoàn tiền mô phỏng.'}</p>
      <label className="cancel-reason">Lý do (không bắt buộc)
        <textarea value={reason} maxLength={300} onChange={(event) => setReason(event.target.value)} /></label>
      <div className="address-modal-actions"><button type="button" className="account-secondary" disabled={busy}
        onClick={() => setCancelOpen(false)}>Không hủy</button>
        <button type="button" className="account-primary" disabled={busy}
          onClick={() => { setCancelOpen(false); void run(() => cancelOrder(order.id, reason.trim()), 'Đã hủy đơn hàng.') }}>
          Hủy đơn</button></div>
    </section></div>}
  </div></ShopLayout>
}
