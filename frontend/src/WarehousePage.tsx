import { useCallback, useEffect, useState, type FormEvent } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import DailyReportPanel from './DailyReportPanel'
import {
  errorMessage, listPackingOrders, listProducts, packOrder, restockProduct,
  type PackingOrder, type PageResponse, type ProductStock,
} from './api/warehouseDelivery'
import { Pager, formatDateTime, formatVnd } from './logisticsUi'
import './logistics.css'

type Tab = 'stock' | 'orders' | 'report'
const NAV: StaffTab<Tab>[] = [
  { id: 'stock', label: 'Tồn kho' },
  { id: 'orders', label: 'Đóng gói' },
  { id: 'report', label: 'Báo cáo ngày' },
]
const PAGE_SIZE = 20

export default function WarehousePage() {
  const [tab, setTab] = useState<Tab>('stock')
  return <StaffLayout eyebrow="Vận hành" title="Hàng đợi kho" roleName="Nhân viên kho"
    tabs={NAV} active={tab} onNavigate={setTab}>
    {tab === 'stock' ? <StockPanel /> : tab === 'orders' ? <PackingPanel /> : <DailyReportPanel team="warehouse" />}
  </StaffLayout>
}

function StockPanel() {
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [lowOnly, setLowOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<ProductStock> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [target, setTarget] = useState<ProductStock | null>(null)
  const [quantity, setQuantity] = useState('')
  const [reference, setReference] = useState('')
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try { setData(await listProducts({ search: query, lowStockOnly: lowOnly, page, size: PAGE_SIZE })) }
    catch (e) { setError(errorMessage(e)) }
    finally { setLoading(false) }
  }, [query, lowOnly, page])
  useEffect(() => { void load() }, [load])

  function openRestock(product: ProductStock) {
    setTarget(product); setQuantity(''); setReference(''); setNotes(''); setNotice(''); setError('')
  }

  async function submitRestock(event: FormEvent) {
    event.preventDefault()
    if (!target) return
    const qty = Number(quantity)
    if (!Number.isInteger(qty) || qty < 1 || qty > 100000) {
      setError('Số lượng nhập phải là số nguyên từ 1 đến 100.000.')
      return
    }
    setSaving(true)
    setError('')
    try {
      const result = await restockProduct(target.id, {
        quantity: qty, referenceId: reference.trim() || undefined, notes: notes.trim() || undefined,
      })
      setNotice(`Đã nhập ${qty} × ${target.sku}. Tồn kho mới: ${result.product.onHandQty}.`)
      setTarget(null)
      await load()
    } catch (e) { setError(errorMessage(e)) }
    finally { setSaving(false) }
  }

  return <section>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    {notice && <div className="lg-msg ok" role="status">{notice}</div>}

    {target && <form className="lg-panel" onSubmit={submitRestock}>
      <h3>Nhập kho: {target.name} ({target.sku})</h3>
      <p>Tồn hiện tại {target.onHandQty}, đang giữ {target.reservedQty}.</p>
      <div className="lg-field"><label htmlFor="rs-qty">Số lượng nhập</label>
        <input id="rs-qty" type="number" min={1} max={100000} value={quantity} required
          onChange={(e) => setQuantity(e.target.value)} /></div>
      <div className="lg-field"><label htmlFor="rs-ref">Mã tham chiếu (phiếu nhập, PO)</label>
        <input id="rs-ref" maxLength={100} value={reference} onChange={(e) => setReference(e.target.value)} /></div>
      <div className="lg-field"><label htmlFor="rs-notes">Ghi chú</label>
        <textarea id="rs-notes" rows={3} maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
      <div className="lg-actions">
        <button type="submit" className="lg-btn primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Xác nhận nhập kho'}</button>
        <button type="button" className="lg-btn ghost" onClick={() => setTarget(null)}>Hủy</button>
      </div>
    </form>}

    <form className="lg-toolbar" onSubmit={(e) => { e.preventDefault(); setPage(0); setQuery(search) }}>
      <input type="search" placeholder="Tìm theo tên hoặc SKU" aria-label="Tìm sản phẩm" value={search}
        onChange={(e) => setSearch(e.target.value)} />
      <label><input type="checkbox" checked={lowOnly}
        onChange={(e) => { setPage(0); setLowOnly(e.target.checked) }} /> Chỉ hàng sắp hết</label>
      <button type="submit" className="lg-btn primary">Tìm</button>
    </form>

    <div className="lg-tablewrap"><table className="lg-table">
      <thead><tr><th>SKU</th><th>Sản phẩm</th><th className="num">Giá</th><th className="num">Tồn kho</th>
        <th className="num">Đang giữ</th><th className="num">Khả dụng</th><th>Thao tác</th></tr></thead>
      <tbody>
        {data?.items.map((p) => <tr key={p.id}>
          <td>{p.sku}</td><td>{p.name}<small>{p.status}</small></td>
          <td className="num">{formatVnd(p.price)}</td>
          <td className="num">{p.onHandQty}</td><td className="num">{p.reservedQty}</td>
          <td className={`num${p.lowStock ? ' lg-low' : ''}`}>{p.availableQty}{p.lowStock ? ' · sắp hết' : ''}</td>
          <td><button type="button" className="lg-btn primary" onClick={() => openRestock(p)}>Nhập kho</button></td>
        </tr>)}
      </tbody>
    </table>
      {!loading && data?.items.length === 0 && <div className="lg-empty">Không có sản phẩm phù hợp.</div>}
      {loading && <div className="lg-empty">Đang tải…</div>}
    </div>
    {data && <Pager page={page} size={PAGE_SIZE} total={data.total} onPage={setPage} />}
  </section>
}

function PackingPanel() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<PackingOrder> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try { setData(await listPackingOrders(page, PAGE_SIZE)) }
    catch (e) { setError(errorMessage(e)) }
    finally { setLoading(false) }
  }, [page])
  useEffect(() => { void load() }, [load])

  async function pack(order: PackingOrder) {
    if (!window.confirm(`Đóng gói đơn ${order.orderCode}? Hệ thống sẽ trừ tồn kho và tạo vận đơn.`)) return
    setBusyId(order.id)
    setError('')
    setNotice('')
    try {
      const result = await packOrder(order.id)
      setNotice(`Đã đóng gói ${order.orderCode}. Mã vận đơn: ${result.shipment.trackingNumber ?? '—'}.`)
      await load()
    } catch (e) { setError(errorMessage(e)) }
    finally { setBusyId(null) }
  }

  return <section>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    {notice && <div className="lg-msg ok" role="status">{notice}</div>}
    <div className="lg-tablewrap"><table className="lg-table">
      <thead><tr><th>Đơn hàng</th><th>Người nhận</th><th>Sản phẩm</th><th>Thanh toán</th><th>Trạng thái</th>
        <th className="num">Tổng tiền</th><th>Thao tác</th></tr></thead>
      <tbody>
        {data?.items.map((o) => <tr key={o.id}>
          <td>{o.orderCode}<small>{formatDateTime(o.createdAt)}</small></td>
          <td>{o.recipientName}<small>{o.recipientPhone}</small><small>{o.deliveryAddress}</small></td>
          <td>{o.items.map((i) => <div key={`${o.id}-${i.productId}-${i.sku}`}>{i.productName} × {i.quantity}<small>{i.sku}</small></div>)}</td>
          <td>{o.paymentMethod === 'COD' ? 'COD' : 'QR'} · {o.paymentStatus === 'PAID' ? 'đã thanh toán' : 'chưa thanh toán'}</td>
          <td><span className={`lg-chip ${o.status === 'PROCESSING' ? 'lg-warn' : 'lg-info'}`}>
            {o.status === 'PROCESSING' ? 'Đang xử lý' : 'Đã xác nhận'}</span></td>
          <td className="num">{formatVnd(o.totalAmount)}</td>
          <td><button type="button" className="lg-btn primary" disabled={busyId === o.id}
            onClick={() => void pack(o)}>{busyId === o.id ? 'Đang xử lý…' : 'Đóng gói'}</button></td>
        </tr>)}
      </tbody>
    </table>
      {!loading && data?.items.length === 0 && <div className="lg-empty">Không có đơn nào đang chờ đóng gói.</div>}
      {loading && <div className="lg-empty">Đang tải…</div>}
    </div>
    {data && <Pager page={page} size={PAGE_SIZE} total={data.total} onPage={setPage} />}
  </section>
}
