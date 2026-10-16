import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listOrders, type OrderStatus, type OrderSummary } from './api/orders'
import AccountLayout from './AccountLayout'
import { formatDateTime, formatVnd, orderStatusNames, paymentMethodNames, statusTone } from './orderFormat'
import { Icon } from './shopUi'
import { getUiError } from './uiError'
import './orders.css'

/** The newest orders are loaded at once and grouped on the client, like the tabs of a typical shop account. */
const RECENT_LIMIT = 100
const tabs: { id: string; label: string; statuses: OrderStatus[] | null }[] = [
  { id: 'all', label: 'Tất cả', statuses: null },
  { id: 'pay', label: 'Chờ thanh toán', statuses: ['AWAITING_PAYMENT'] },
  { id: 'processing', label: 'Đang xử lý', statuses: ['PENDING_CONFIRMATION', 'CONFIRMED', 'READY_FOR_DELIVERY'] },
  { id: 'shipping', label: 'Đang giao', statuses: ['ASSIGNED', 'DELIVERING', 'DELIVERY_FAILED'] },
  { id: 'done', label: 'Đã giao', statuses: ['DELIVERED'] },
  { id: 'cancelled', label: 'Đã hủy', statuses: ['CANCELLED', 'RETURNED_TO_WAREHOUSE'] },
]

export default function OrdersPage() {
  const [orders, setOrders] = useState<OrderSummary[]>([])
  const [total, setTotal] = useState(0)
  const [tab, setTab] = useState('all')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    listOrders(0, RECENT_LIMIT).then((data) => { if (active) { setOrders(data.items); setTotal(data.totalItems) } })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  const counts = useMemo(() => Object.fromEntries(tabs.map((item) => [item.id,
    item.statuses ? orders.filter((order) => item.statuses!.includes(order.status)).length : orders.length])), [orders])
  const statuses = tabs.find((item) => item.id === tab)?.statuses
  const visible = statuses ? orders.filter((order) => statuses.includes(order.status)) : orders

  return <AccountLayout title="Đơn hàng của tôi" intro="Theo dõi trạng thái, thanh toán và hủy đơn trước khi đóng gói.">
    <div className="order-tabs" role="tablist" aria-label="Lọc đơn hàng">
      {tabs.map((item) => <button key={item.id} type="button" role="tab" aria-selected={tab === item.id}
        className={tab === item.id ? 'active' : ''} onClick={() => setTab(item.id)}>
        {item.label}{counts[item.id] > 0 && <span>{counts[item.id]}</span>}</button>)}
    </div>
    {loading && <div className="checkout-skeleton" role="status">Đang tải đơn hàng…</div>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    {!loading && !error && visible.length === 0 && <section className="shop-card empty-state">
      <Icon name="bag" size={38} />
      <h2>{orders.length === 0 ? 'Bạn chưa có đơn hàng nào' : 'Không có đơn ở mục này'}</h2>
      <p>{orders.length === 0 ? 'Các đơn hàng đã đặt sẽ xuất hiện ở đây.' : 'Hãy chọn mục khác để xem đơn hàng.'}</p>
      {orders.length === 0 && <Link className="shop-button" to="/">Bắt đầu mua sắm</Link>}</section>}
    <div className="order-cards">{visible.map((order) => <article key={order.id} className="order-card">
      <header><div><strong>{order.orderCode}</strong><span>Đặt lúc {formatDateTime(order.createdAt)}</span></div>
        <span className={`status-badge ${statusTone(order.status)}`}>{orderStatusNames[order.status]}</span></header>
      <div className="order-card-body">
        <span className="order-card-icon"><Icon name={order.paymentMethod === 'COD' ? 'cash' : 'qr'} size={22} /></span>
        <div><p>{order.itemCount} sản phẩm · {paymentMethodNames[order.paymentMethod]}</p>
          {order.status === 'AWAITING_PAYMENT' && <p className="warn-text">Đơn đang chờ bạn thanh toán</p>}</div>
        <div className="order-card-total"><span>Tổng tiền</span><strong>{formatVnd(order.totalAmount)}</strong></div>
      </div>
      <footer>
        {order.status === 'AWAITING_PAYMENT' && <Link className="shop-button small" to={`/me/orders/${order.id}`}>Thanh toán ngay</Link>}
        <Link className="shop-button ghost small" to={`/me/orders/${order.id}`}>Xem chi tiết</Link>
      </footer>
    </article>)}</div>
    {total > RECENT_LIMIT && <p className="muted">Đang hiển thị {RECENT_LIMIT} đơn hàng gần nhất.</p>}
  </AccountLayout>
}
