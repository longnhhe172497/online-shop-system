import { useCallback, useEffect, useState } from 'react'
import { cancelCodOrder, confirmCodOrder, listPendingCodOrders, type StaffOrder } from './api/staffOrders'
import { Pager } from './logisticsUi'
import { formatDateTime, formatVnd } from './orderFormat'
import { getUiError } from './uiError'
import './logistics.css'
import './staffOrders.css'

const PAGE_SIZE = 10

/** Support Staff confirm or cancel COD orders before the warehouse packs them (UC-SUP-001). */
export default function SupportOrdersPage() {
  const [orders, setOrders] = useState<StaffOrder[]>([])
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [notes, setNotes] = useState<Record<number, string>>({})
  const [cancelling, setCancelling] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState<number | null>(null)

  const load = useCallback(async (target: number) => {
    setLoading(true)
    try {
      const data = await listPendingCodOrders(target, PAGE_SIZE)
      setOrders(data.items); setTotal(data.totalItems); setError('')
    } catch (failure) { setError(getUiError(failure)) }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { void Promise.resolve().then(() => load(page)) }, [load, page])

  async function act(order: StaffOrder, action: 'confirm' | 'cancel') {
    setBusy(order.id); setError(''); setNotice('')
    try {
      if (action === 'confirm') {
        await confirmCodOrder(order.id, notes[order.id]?.trim() ?? '')
        setNotice(`Đã xác nhận đơn ${order.orderCode}. Kho có thể đóng gói đơn này.`)
      } else {
        await cancelCodOrder(order.id, reason.trim())
        setNotice(`Đã hủy đơn ${order.orderCode}. Hàng giữ cho đơn đã được trả lại kho.`)
        setCancelling(null); setReason('')
      }
      await load(page)
    } catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(null) }
  }

  return <section className="staff-orders">
    <div className="lg-toolbar"><strong>{total} đơn COD chờ xác nhận</strong>
      <span className="lg-chip lg-muted">Cũ nhất ở trên cùng</span>
      <button type="button" className="lg-btn ghost" onClick={() => void load(page)} disabled={loading}>Tải lại</button></div>
    {error && <p className="lg-msg lg-msg-error" role="alert">{error}</p>}
    {notice && <p className="lg-msg" role="status">{notice}</p>}
    {loading && orders.length === 0 && <p className="lg-empty">Đang tải đơn hàng…</p>}
    {!loading && !error && orders.length === 0 && <p className="lg-empty">Không có đơn COD nào chờ xác nhận.</p>}

    {orders.map((order) => <article key={order.id} className="lg-panel staff-order-card">
      <header className="staff-order-head">
        <div><strong>{order.orderCode}</strong><small>Đặt lúc {formatDateTime(order.createdAt)}</small></div>
        <span className="lg-chip lg-warn">Chờ xác nhận · COD</span>
        <strong className="staff-order-total">{formatVnd(order.totalAmount)}</strong>
      </header>
      <div className="staff-order-grid">
        <dl className="staff-order-info">
          <div><dt>Khách hàng</dt><dd>{order.customerName}<small>{order.customerEmail}</small></dd></div>
          <div><dt>Người nhận</dt><dd>{order.recipientName}<small>{order.recipientPhone}</small></dd></div>
          <div><dt>Giao đến</dt><dd>{order.shippingAddress}</dd></div>
          {order.voucherCode && <div><dt>Mã giảm giá</dt><dd>{order.voucherCode} (− {formatVnd(order.discountAmount)})</dd></div>}
        </dl>
        <div className="lg-tablewrap"><table className="lg-table">
          <thead><tr><th>Sản phẩm</th><th className="num">SL</th><th className="num">Thành tiền</th></tr></thead>
          <tbody>{order.items.map((item) => <tr key={item.id}><td>{item.name}<small>{item.sku}</small></td>
            <td className="num">{item.quantity}</td><td className="num">{formatVnd(item.lineTotal)}</td></tr>)}</tbody>
        </table></div>
      </div>
      {cancelling === order.id ? <div className="staff-order-actions">
        <label className="lg-field staff-wide">Lý do hủy (bắt buộc)
          <textarea className="lg-textarea" value={reason} maxLength={300} onChange={(event) => setReason(event.target.value)}
            placeholder="Ví dụ: Không liên lạc được khách sau 3 lần gọi" /></label>
        <div className="lg-actions">
          <button type="button" className="lg-btn ghost" disabled={busy === order.id}
            onClick={() => { setCancelling(null); setReason('') }}>Quay lại</button>
          <button type="button" className="lg-btn danger" disabled={busy === order.id || !reason.trim()}
            onClick={() => void act(order, 'cancel')}>Xác nhận hủy đơn</button>
        </div>
      </div> : <div className="staff-order-actions">
        <label className="lg-field staff-wide">Ghi chú nội bộ (không bắt buộc)
          <input value={notes[order.id] ?? ''} maxLength={500} placeholder="Ví dụ: Đã gọi khách lúc 9h, khách xác nhận"
            onChange={(event) => setNotes({ ...notes, [order.id]: event.target.value })} /></label>
        <div className="lg-actions">
          <button type="button" className="lg-btn danger" disabled={busy === order.id}
            onClick={() => { setCancelling(order.id); setReason('') }}>Hủy đơn</button>
          <button type="button" className="lg-btn primary" disabled={busy === order.id}
            onClick={() => void act(order, 'confirm')}>{busy === order.id ? 'Đang xử lý…' : 'Xác nhận đơn'}</button>
        </div>
      </div>}
    </article>)}
    {total > PAGE_SIZE && <Pager page={page} size={PAGE_SIZE} total={total} onPage={setPage} />}
  </section>
}
