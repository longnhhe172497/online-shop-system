import { useCallback, useEffect, useState } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import DailyReportPanel from './DailyReportPanel'
import {
  errorMessage, listPackingOrders, packOrder,
  type PackingOrder, type PageResponse,
} from './api/warehouseDelivery'
import { Pager, formatDateTime, formatVnd } from './logisticsUi'
import './logistics.css'

type Tab = 'orders' | 'returns' | 'report'
const NAV: StaffTab<Tab>[] = [
  { id: 'orders', label: 'Đóng gói' },
  { id: 'returns', label: 'Kiểm định hoàn trả' },
  { id: 'report', label: 'Báo cáo ngày' },
]
const PAGE_SIZE = 20

export default function WarehousePage() {
  const [tab, setTab] = useState<Tab>('orders')
  return <StaffLayout eyebrow="Vận hành" title="Hàng đợi kho" roleName="Nhân viên kho"
    tabs={NAV} active={tab} onNavigate={setTab}>
    {tab === 'orders' ? <PackingPanel /> : tab === 'returns' ? <ReturnInspectionPanel /> : <DailyReportPanel team="warehouse" />}
  </StaffLayout>
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

interface ReturnInspectionItem {
  id: number
  requestCode: string
  orderCode: string
  productName: string
  sku: string
  quantity: number
  reason: string
  status: 'PENDING' | 'INSPECTING' | 'APPROVED' | 'REJECTED'
  notes: string
}

const INITIAL_RETURN_ITEMS: ReturnInspectionItem[] = [
  {
    id: 103,
    requestCode: 'RR-103',
    orderCode: 'ORD-WH-DELIV-03',
    productName: 'Áo Khoác Hoodie Nỉ Bông · L',
    sku: 'SKU-HD-003',
    quantity: 1,
    reason: 'Kích cỡ quá rộng so với mô tả',
    status: 'PENDING',
    notes: '',
  },
  {
    id: 102,
    requestCode: 'RR-102',
    orderCode: 'ORD-WH-DELIV-02',
    productName: 'Quần Jeans Slimfit Co Giãn · M',
    sku: 'SKU-JN-002',
    quantity: 1,
    reason: 'Sản phẩm bị bám bẩn khi nhận hàng',
    status: 'INSPECTING',
    notes: '',
  },
  {
    id: 104,
    requestCode: 'RR-104',
    orderCode: 'ORD-WH-DELIV-04',
    productName: 'Áo Sơ Mi Oxford Trắng · L',
    sku: 'SKU-SM-004',
    quantity: 1,
    reason: 'Đường chỉ ở cổ áo bị bung nhẹ',
    status: 'PENDING',
    notes: '',
  },
  {
    id: 105,
    requestCode: 'RR-105',
    orderCode: 'ORD-WH-DELIV-05',
    productName: 'Quần Kaki Túi Hộp Cargo · XL',
    sku: 'SKU-KK-005',
    quantity: 2,
    reason: 'Giao nhầm màu sản phẩm',
    status: 'PENDING',
    notes: '',
  },
  {
    id: 101,
    requestCode: 'RR-101',
    orderCode: 'ORD-WH-DELIV-01',
    productName: 'Áo Thun Oversize Cotton · L',
    sku: 'SKU-AT-001',
    quantity: 1,
    reason: 'Khách đổi ý không còn nhu cầu',
    status: 'APPROVED',
    notes: 'Hàng còn nguyên tem mác, bao bì sạch sẽ, đã nhập lại kho.',
  },
]

function ReturnInspectionPanel() {
  const [items, setItems] = useState<ReturnInspectionItem[]>(INITIAL_RETURN_ITEMS)
  const [selectedId, setSelectedId] = useState<number>(INITIAL_RETURN_ITEMS[0].id)
  const [notes, setNotes] = useState<string>(INITIAL_RETURN_ITEMS[0].notes)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)

  const selected = items.find((item) => item.id === selectedId) ?? items[0]
  const isFinalized = selected.status === 'APPROVED' || selected.status === 'REJECTED'

  function selectRequest(item: ReturnInspectionItem) {
    setSelectedId(item.id)
    setNotes(item.notes)
    setError('')
    setNotice('')
  }

  function handleInspect(decision: 'APPROVED' | 'REJECTED') {
    if (!selected || isFinalized) return
    const trimmedNotes = notes.trim()

    if (decision === 'REJECTED' && !trimmedNotes) {
      setError('Vui lòng nhập ghi chú kiểm định nêu rõ lý do trước khi Từ chối.')
      setNotice('')
      return
    }

    setSaving(true)
    setError('')
    setNotice('')

    const finalNotes =
      trimmedNotes ||
      (decision === 'APPROVED'
        ? 'Hàng nguyên tem mác, đạt tiêu chuẩn nhập lại kho.'
        : '')

    setItems((prev) =>
      prev.map((item) =>
        item.id === selected.id
          ? { ...item, status: decision, notes: finalNotes }
          : item
      )
    )
    setNotes(finalNotes)

    if (decision === 'APPROVED') {
      setNotice(
        `Đã chấp nhận yêu cầu hoàn trả ${selected.requestCode} (Đơn ${selected.orderCode}). Đã cập nhật nhập lại kho và chuyển hoàn tiền.`
      )
    } else {
      setNotice(
        `Đã từ chối yêu cầu hoàn trả ${selected.requestCode} (Đơn ${selected.orderCode}). Lý do: ${finalNotes}`
      )
    }
    setSaving(false)
  }

  function renderStatusChip(status: ReturnInspectionItem['status']) {
    switch (status) {
      case 'PENDING':
        return <span className="lg-chip lg-warn">Chờ kiểm định</span>
      case 'INSPECTING':
        return <span className="lg-chip lg-info">Đang kiểm định</span>
      case 'APPROVED':
        return <span className="lg-chip lg-ok">Đã chấp nhận</span>
      case 'REJECTED':
        return <span className="lg-chip lg-bad">Đã từ chối</span>
    }
  }

  return (
    <section>
      {error && <div className="lg-msg err" role="alert">{error}</div>}
      {notice && <div className="lg-msg ok" role="status">{notice}</div>}

      <div className="lg-split">
        <div className="lg-tablewrap">
          <table className="lg-table">
            <thead>
              <tr>
                <th>Yêu cầu</th>
                <th>Đơn hàng</th>
                <th>Sản phẩm</th>
                <th>Lý do</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const active = item.id === selected.id
                return (
                  <tr
                    key={item.id}
                    onClick={() => selectRequest(item)}
                    style={{
                      cursor: 'pointer',
                      background: active ? '#f2f0e9' : undefined,
                    }}
                  >
                    <td><strong>{item.requestCode}</strong></td>
                    <td>{item.orderCode}</td>
                    <td>
                      {item.productName} × {item.quantity}
                      <small>{item.sku}</small>
                    </td>
                    <td>{item.reason}</td>
                    <td>{renderStatusChip(item.status)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {items.length === 0 && (
            <div className="lg-empty">Không có yêu cầu hoàn trả nào cần kiểm định.</div>
          )}
        </div>

        {selected && (
          <div className="lg-panel">
            <h3>Kiểm định {selected.requestCode}</h3>
            <p style={{ margin: '0 0 14px', color: 'var(--lg-muted)', fontSize: '13px' }}>
              Đơn hàng: <strong>{selected.orderCode}</strong> · {selected.productName} × {selected.quantity} ({selected.sku})
            </p>
            <div className="lg-field" style={{ maxWidth: '100%' }}>
              <label htmlFor="inspect-notes">Ghi chú kiểm định</label>
              <textarea
                id="inspect-notes"
                rows={4}
                maxLength={1000}
                disabled={isFinalized || saving}
                placeholder="Tình trạng tem mác, bao bì, ghi chú nhập lại kho..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
            <div className="lg-actions">
              <button
                type="button"
                className="lg-btn primary"
                disabled={isFinalized || saving}
                onClick={() => handleInspect('APPROVED')}
              >
                Chấp nhận
              </button>
              <button
                type="button"
                className="lg-btn ghost"
                disabled={isFinalized || saving}
                onClick={() => handleInspect('REJECTED')}
              >
                Từ chối
              </button>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}