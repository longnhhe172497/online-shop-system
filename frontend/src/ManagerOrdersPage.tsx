import { useCallback, useEffect, useState } from 'react'
import type { OrderStatus } from './api/orders'
import { assignDeliveryStaff, getManagerOrder, listDeliveryStaff, listManagerOrders, type DeliveryStaffOption,
  type StaffOrder, type StaffOrderSummary } from './api/staffOrders'
import { Pager } from './logisticsUi'
import { attemptStatusNames, formatDateTime, formatVnd, orderStatusNames, paymentMethodNames, paymentStatusNames,
  shipmentStatusNames } from './orderFormat'
import { getUiError } from './uiError'
import './logistics.css'
import './staffOrders.css'

const PAGE_SIZE = 20
const FILTERS: { value: '' | OrderStatus; label: string }[] = [
  { value: '', label: 'Tất cả' },
  { value: 'READY_FOR_DELIVERY', label: 'Chờ phân công' },
  { value: 'PENDING_CONFIRMATION', label: 'Chờ xác nhận' },
  { value: 'AWAITING_PAYMENT', label: 'Chờ thanh toán' },
  { value: 'CONFIRMED', label: 'Chờ đóng gói' },
  { value: 'ASSIGNED', label: 'Đã phân công' },
  { value: 'DELIVERING', label: 'Đang giao' },
  { value: 'DELIVERY_FAILED', label: 'Giao thất bại' },
  { value: 'DELIVERED', label: 'Đã giao' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

function tone(status: OrderStatus) {
  if (status === 'DELIVERED') return 'lg-ok'
  if (status === 'CANCELLED' || status === 'DELIVERY_FAILED' || status === 'RETURNED_TO_WAREHOUSE') return 'lg-bad'
  if (status === 'READY_FOR_DELIVERY' || status === 'PENDING_CONFIRMATION' || status === 'AWAITING_PAYMENT') return 'lg-warn'
  return 'lg-info'
}

/** Operational Manager: every order (UC-MNG-008) and shipper assignment for packed orders (UC-MNG-009). */
export default function ManagerOrdersPage() {
  const [status, setStatus] = useState<'' | OrderStatus>('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [orders, setOrders] = useState<StaffOrderSummary[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [selected, setSelected] = useState<StaffOrder | null>(null)
  const [staff, setStaff] = useState<DeliveryStaffOption[]>([])
  const [shipper, setShipper] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listManagerOrders({ status, search: search.trim(), page, size: PAGE_SIZE })
      setOrders(data.items); setTotal(data.totalItems); setError('')
    } catch (failure) { setError(getUiError(failure)) }
    finally { setLoading(false) }
  }, [status, search, page])
  useEffect(() => { const timer = window.setTimeout(() => { void load() }, 250); return () => window.clearTimeout(timer) }, [load])

  async function open(id: number) {
    setError(''); setNotice('')
    try {
      const order = await getManagerOrder(id)
      setSelected(order); setShipper('')
      if (order.assignable) setStaff(await listDeliveryStaff())
    } catch (failure) { setError(getUiError(failure)) }
  }

  async function assign() {
    if (!selected || !shipper) return
    setBusy(true); setError(''); setNotice('')
    try {
      const order = await assignDeliveryStaff(selected.id, Number(shipper))
      setSelected(order)
      setNotice(`Đã phân công đơn ${order.orderCode} cho ${order.shipment?.deliveryStaffName ?? 'nhân viên giao hàng'}.`)
      setStaff(await listDeliveryStaff())
      await load()
    } catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  return <section className="staff-orders">
    <div className="staff-filter-chips" role="tablist" aria-label="Lọc theo trạng thái">
      {FILTERS.map((filter) => <button key={filter.value || 'all'} type="button" role="tab"
        aria-selected={status === filter.value} className={`lg-chip ${status === filter.value ? 'lg-info' : 'lg-muted'}`}
        onClick={() => { setStatus(filter.value); setPage(0) }}>{filter.label}</button>)}
    </div>
    <div className="lg-toolbar">
      <input className="staff-search" aria-label="Tìm đơn hàng" placeholder="Tìm theo mã đơn, tên/email khách, số điện thoại…"
        value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
      <span>{total} đơn</span>
    </div>
    {error && <p className="lg-msg lg-msg-error" role="alert">{error}</p>}
    {notice && <p className="lg-msg" role="status">{notice}</p>}

    <div className={selected ? 'lg-split' : ''}>
      <div>
        {!loading && orders.length === 0 && <p className="lg-empty">Không có đơn hàng phù hợp.</p>}
        {orders.length > 0 && <div className="lg-tablewrap"><table className="lg-table staff-order-table">
          <thead><tr><th>Mã đơn</th><th>Khách hàng</th><th className="num">Tổng tiền</th><th>Thanh toán</th>
            <th>Trạng thái</th><th>Giao hàng</th><th></th></tr></thead>
          <tbody>{orders.map((order) => <tr key={order.id} className={selected?.id === order.id ? 'selected' : ''}>
            <td><strong>{order.orderCode}</strong><small>{formatDateTime(order.createdAt)}</small></td>
            <td>{order.customerName}<small>{order.recipientPhone}</small></td>
            <td className="num">{formatVnd(order.totalAmount)}<small>{order.itemCount} sản phẩm</small></td>
            <td>{paymentMethodNames[order.paymentMethod]}
              <small>{order.paymentStatus ? paymentStatusNames[order.paymentStatus] : '—'}</small></td>
            <td><span className={`lg-chip ${tone(order.status)}`}>{orderStatusNames[order.status]}</span></td>
            <td>{order.deliveryStaffName ?? (order.status === 'READY_FOR_DELIVERY' ? 'Chưa phân công' : '—')}</td>
            <td><button type="button" className="lg-btn ghost" onClick={() => void open(order.id)}>Xem</button></td>
          </tr>)}</tbody>
        </table></div>}
        {total > PAGE_SIZE && <Pager page={page} size={PAGE_SIZE} total={total} onPage={setPage} />}
      </div>

      {selected && <aside className="lg-panel staff-order-detail" aria-label={`Chi tiết đơn ${selected.orderCode}`}>
        <header className="staff-order-head">
          <div><strong>{selected.orderCode}</strong><small>Đặt lúc {formatDateTime(selected.createdAt)}</small></div>
          <span className={`lg-chip ${tone(selected.status)}`}>{orderStatusNames[selected.status]}</span>
          <button type="button" className="lg-btn ghost" aria-label="Đóng chi tiết" onClick={() => setSelected(null)}>×</button>
        </header>
        <dl className="staff-order-info">
          <div><dt>Khách hàng</dt><dd>{selected.customerName}<small>{selected.customerEmail}</small></dd></div>
          <div><dt>Người nhận</dt><dd>{selected.recipientName} · {selected.recipientPhone}<small>{selected.shippingAddress}</small></dd></div>
          <div><dt>Thanh toán</dt><dd>{paymentMethodNames[selected.paymentMethod]}
            <small>{selected.paymentStatus ? paymentStatusNames[selected.paymentStatus] : '—'}</small></dd></div>
          {selected.confirmedAt && <div><dt>Xác nhận</dt><dd>{formatDateTime(selected.confirmedAt)}
            <small>{selected.confirmedBy ?? 'Thanh toán QR thành công'}</small></dd></div>}
          {selected.note && <div><dt>Ghi chú</dt><dd>{selected.note}</dd></div>}
        </dl>
        <div className="lg-tablewrap"><table className="lg-table">
          <thead><tr><th>Sản phẩm</th><th className="num">SL</th><th className="num">Thành tiền</th></tr></thead>
          <tbody>{selected.items.map((item) => <tr key={item.id}><td>{item.name}<small>{item.sku}</small></td>
            <td className="num">{item.quantity}</td><td className="num">{formatVnd(item.lineTotal)}</td></tr>)}</tbody>
        </table></div>
        <div className="lg-summary-row"><span>Tổng cộng{selected.voucherCode && ` (mã ${selected.voucherCode})`}</span>
          <strong>{formatVnd(selected.totalAmount)}</strong></div>

        {selected.shipment && <div className="staff-shipment">
          <h3>Vận chuyển · {selected.shipment.trackingNumber}</h3>
          <p>{shipmentStatusNames[selected.shipment.status]}
            {selected.shipment.deliveryStaffName && ` · ${selected.shipment.deliveryStaffName}`}
            {selected.shipment.codAmount > 0 && ` · COD ${formatVnd(selected.shipment.codAmount)}`}</p>
          {selected.shipment.attempts.length > 0 && <ul>{selected.shipment.attempts.map((attempt) =>
            <li key={attempt.attemptNumber}>Lần {attempt.attemptNumber}: {attemptStatusNames[attempt.status]}
              {attempt.failureReason && ` – ${attempt.failureReason}`}</li>)}</ul>}
        </div>}

        {selected.assignable && <div className="staff-assign">
          <label className="lg-field">{selected.shipment?.deliveryStaffName ? 'Đổi nhân viên giao hàng' : 'Phân công nhân viên giao hàng'}
            <select value={shipper} onChange={(event) => setShipper(event.target.value)}>
              <option value="">— Chọn nhân viên —</option>
              {staff.map((member) => <option key={member.id} value={member.id}>
                {member.fullName} · đang giữ {member.activeShipments} đơn</option>)}
            </select></label>
          {staff.length === 0 && <p className="lg-low">Chưa có nhân viên giao hàng đang hoạt động.</p>}
          <button type="button" className="lg-btn primary" disabled={busy || !shipper} onClick={() => void assign()}>
            {busy ? 'Đang phân công…' : 'Phân công'}</button>
        </div>}
      </aside>}
    </div>
  </section>
}
