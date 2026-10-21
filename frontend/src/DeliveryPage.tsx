import { useCallback, useEffect, useState, type FormEvent } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import DailyReportPanel from './DailyReportPanel'
import {
  errorMessage, listShipments, updateShipmentStatus,
  type DeliveryScope, type PageResponse, type ShipmentListItem, type ShipmentStatus,
  type UpdateShipmentStatusRequest,
} from './api/warehouseDelivery'
import { Pager, STATUS_LABELS, StatusChip, formatDateTime, formatVnd } from './logisticsUi'
import './logistics.css'

type Tab = 'MINE' | 'UNASSIGNED' | 'FAILED' | 'REPORT'
const NAV: StaffTab<Tab>[] = [
  { id: 'MINE', label: 'Đơn của tôi' },
  { id: 'UNASSIGNED', label: 'Chưa có người nhận' },
  { id: 'FAILED', label: 'Giao thất bại' },
  { id: 'REPORT', label: 'Báo cáo ngày' },
]
const STATUS_FILTERS: ShipmentStatus[] = ['PENDING_PICKUP', 'READY_FOR_DISPATCH', 'SHIPPING']
const PAGE_SIZE = 20

interface PendingAction { kind: 'deliver' | 'fail'; shipment: ShipmentListItem }

export default function DeliveryPage() {
  const [tab, setTab] = useState<Tab>('MINE')
  const [filter, setFilter] = useState<'' | ShipmentStatus>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<ShipmentListItem> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [action, setAction] = useState<PendingAction | null>(null)
  const [codCollected, setCodCollected] = useState(false)
  const [reason, setReason] = useState('')
  const [note, setNote] = useState('')

  const load = useCallback(async () => {
    if (tab === 'REPORT') return
    setLoading(true)
    setError('')
    const scope: DeliveryScope = tab === 'UNASSIGNED' ? 'UNASSIGNED' : 'MINE'
    const status: ShipmentStatus | undefined = tab === 'FAILED' ? 'DELIVERY_FAILED' : tab === 'MINE' && filter ? filter : undefined
    try { setData(await listShipments({ scope, status, page, size: PAGE_SIZE })) }
    catch (e) { setError(errorMessage(e)) }
    finally { setLoading(false) }
  }, [tab, filter, page])
  useEffect(() => { void load() }, [load])

  function changeTab(next: Tab) { setTab(next); setPage(0); setAction(null); setNotice(''); setError('') }

  function openAction(kind: PendingAction['kind'], shipment: ShipmentListItem) {
    setAction({ kind, shipment }); setCodCollected(false); setReason(''); setNote(''); setNotice(''); setError('')
  }

  async function send(shipment: ShipmentListItem, body: UpdateShipmentStatusRequest, success: string) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await updateShipmentStatus(shipment.id, body)
      setNotice(`${success} (${shipment.orderCode}).`)
      setAction(null)
      await load()
    } catch (e) { setError(errorMessage(e)) }
    finally { setBusy(false) }
  }

  function submitAction(event: FormEvent) {
    event.preventDefault()
    if (!action) return
    const { kind, shipment } = action
    if (kind === 'deliver') {
      if (shipment.codAmount > 0 && !codCollected) { setError('Cần xác nhận đã thu đủ tiền COD.'); return }
      void send(shipment, { status: 'DELIVERED', codCollected, note: note.trim() || undefined }, 'Đã ghi nhận giao thành công')
    } else {
      if (!reason.trim()) { setError('Cần nhập lý do giao thất bại.'); return }
      void send(shipment, { status: 'DELIVERY_FAILED', failedReason: reason.trim(), note: note.trim() || undefined },
        'Đã ghi nhận giao thất bại')
    }
  }

  return <StaffLayout eyebrow="Giao vận" title="Hàng đợi giao hàng" roleName="Nhân viên giao hàng"
    tabs={NAV} active={tab} onNavigate={changeTab}>
    {tab === 'REPORT' ? <DailyReportPanel team="delivery" /> : <>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    {notice && <div className="lg-msg ok" role="status">{notice}</div>}

    {action && <form className="lg-panel" onSubmit={submitAction}>
      <h3>{action.kind === 'deliver' ? 'Xác nhận giao thành công' : 'Báo giao thất bại'}: {action.shipment.orderCode}</h3>
      {action.kind === 'deliver' && action.shipment.codAmount > 0 && <div className="lg-field">
        <label><input type="checkbox" checked={codCollected} onChange={(e) => setCodCollected(e.target.checked)} />
          {' '}Đã thu đủ {formatVnd(action.shipment.codAmount)} tiền COD</label></div>}
      {action.kind === 'fail' && <div className="lg-field"><label htmlFor="dl-reason">Lý do giao thất bại</label>
        <input id="dl-reason" maxLength={255} value={reason} required onChange={(e) => setReason(e.target.value)} /></div>}
      <div className="lg-field"><label htmlFor="dl-note">Ghi chú (không bắt buộc)</label>
        <textarea id="dl-note" rows={3} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} /></div>
      <div className="lg-actions">
        <button type="submit" className={`lg-btn ${action.kind === 'fail' ? 'danger' : 'primary'}`} disabled={busy}>
          {busy ? 'Đang lưu…' : action.kind === 'deliver' ? 'Xác nhận đã giao' : 'Xác nhận thất bại'}</button>
        <button type="button" className="lg-btn ghost" onClick={() => setAction(null)}>Hủy</button>
      </div>
    </form>}

    {tab === 'MINE' && <div className="lg-toolbar">
      <label htmlFor="dl-filter">Trạng thái</label>
      <select id="dl-filter" value={filter} onChange={(e) => { setPage(0); setFilter(e.target.value as '' | ShipmentStatus) }}>
        <option value="">Tất cả đang xử lý</option>
        {STATUS_FILTERS.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
      </select>
    </div>}

    <div className="lg-tablewrap"><table className="lg-table">
      <thead><tr><th>Đơn hàng</th><th>Người nhận</th><th>Địa chỉ</th><th>Trạng thái</th>
        <th>Thanh toán</th><th className="num">Tổng tiền</th><th>Thao tác</th></tr></thead>
      <tbody>
        {data?.items.map((s) => <tr key={s.id}>
          <td>{s.orderCode}<small>{s.trackingNumber ?? '—'}</small><small>{formatDateTime(s.createdAt)}</small></td>
          <td>{s.recipientName}<small>{s.recipientPhone}</small></td>
          <td>{s.deliveryAddress}</td>
          <td><StatusChip status={s.status} />{s.failedReason && <small>{s.failedReason}</small>}</td>
          <td>{s.paymentMethod === 'COD' ? (s.codAmount > 0 ? `COD · thu ${formatVnd(s.codAmount)}` : 'COD · đã thu') : 'QR · đã thanh toán'}</td>
          <td className="num">{formatVnd(s.orderTotal)}</td>
          <td><div className="lg-actions">
            {s.status !== 'SHIPPING' && <button type="button" className="lg-btn primary" disabled={busy}
              onClick={() => void send(s, { status: 'SHIPPING' }, s.status === 'DELIVERY_FAILED' ? 'Đã nhận giao lại' : 'Đã nhận đơn đi giao')}>
              {s.status === 'DELIVERY_FAILED' ? 'Giao lại' : 'Nhận đơn đi giao'}</button>}
            {s.status === 'SHIPPING' && <>
              <button type="button" className="lg-btn primary" disabled={busy} onClick={() => openAction('deliver', s)}>Giao thành công</button>
              <button type="button" className="lg-btn danger" disabled={busy} onClick={() => openAction('fail', s)}>Giao thất bại</button>
            </>}
          </div></td>
        </tr>)}
      </tbody>
    </table>
      {!loading && data?.items.length === 0 && <div className="lg-empty">Không có vận đơn nào trong mục này.</div>}
      {loading && <div className="lg-empty">Đang tải…</div>}
    </div>
    {data && <Pager page={page} size={PAGE_SIZE} total={data.total} onPage={setPage} />}
  </>}
  </StaffLayout>
}
