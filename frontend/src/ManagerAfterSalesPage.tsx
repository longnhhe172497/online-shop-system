import { useCallback, useEffect, useState } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import { formatDateTime, formatVnd } from './logisticsFormat'
import { errorMessage } from './api/warehouseDelivery'
import { inspectionResultLabel, refundMethodLabel, refundReasonLabel, refundStatusLabel, refundStatusTone,
  returnStatusLabel, returnStatusTone } from './afterSalesUi'
import {
  completeRefund, createFailedDeliveryRefund, decideReturn, downloadTransactionReport, getDashboard,
  getTransactionReport, listFailedDeliveryOrders, listRefunds, listReturns, listStaffDailyReports,
  type DashboardView, type FailedDeliveryOrder, type Refund, type ReportSummary, type ReturnRequest,
  type StaffReportRow,
} from './api/afterSales'
import './logistics.css'
import './managerDashboard.css'

type Tab = 'overview' | 'returns' | 'refunds' | 'staff-reports' | 'reports'
const NAV: StaffTab<Tab>[] = [
  { id: 'overview', label: 'Tổng quan' },
  { id: 'returns', label: 'Yêu cầu trả hàng' },
  { id: 'refunds', label: 'Hoàn tiền' },
  { id: 'staff-reports', label: 'Báo cáo nhân viên' },
  { id: 'reports', label: 'Báo cáo giao dịch' },
]

/** Calendar date in Viet Nam time (UTC+7), the reporting time zone of the system. */
function vnDate(offsetDays = 0) {
  const d = new Date(Date.now() + 7 * 3600_000)
  d.setUTCDate(d.getUTCDate() + offsetDays)
  return d.toISOString().slice(0, 10)
}

const MSG_REJECT_NOTE = 'Vui lòng nhập ghi chú lý do từ chối.'
const STAFF_ROLE: Record<string, string> = { WAREHOUSE: 'Nhân viên kho', DELIVERY: 'Nhân viên giao hàng' }

export default function ManagerAfterSalesPage() {
  const [tab, setTab] = useState<Tab>('overview')
  return <StaffLayout eyebrow="Quản lý vận hành" title="Hậu mãi & báo cáo"
    subtitle="Theo dõi kinh doanh, duyệt yêu cầu trả hàng, kiểm soát hoàn tiền và xem báo cáo nhân viên."
    roleName="Quản lý"
    tabs={NAV} active={tab} onNavigate={setTab}>
    {tab === 'overview' && <OverviewPanel onOpen={setTab} />}
    {tab === 'returns' && <ReturnsPanel />}
    {tab === 'refunds' && <RefundsPanel />}
    {tab === 'staff-reports' && <StaffReportsPanel />}
    {tab === 'reports' && <ReportsPanel />}
  </StaffLayout>
}

function OverviewPanel({ onOpen }: { onOpen: (tab: Tab) => void }) {
  const [days, setDays] = useState(30)
  const [data, setData] = useState<DashboardView | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setData(await getDashboard(vnDate(-(days - 1)), vnDate())) } catch (e) { setError(errorMessage(e)) } finally { setLoading(false) }
  }, [days])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  if (error) return <div className="lg-msg err" role="alert">{error}</div>
  if (loading || !data) return <div className="lg-empty">Đang tổng hợp số liệu…</div>
  const s = data.summary
  const max = Math.max(0, ...s.daily.map((d) => d.grossRevenue))
  const p = data.pendingActions
  return <section aria-label="Tổng quan kinh doanh">
    <div className="md-kpis">
      <div className="md-kpi"><span className="md-eyebrow">Doanh thu đã thu</span><strong>{formatVnd(s.grossRevenue)}</strong>
        <small>QR đã thanh toán và COD đã thu</small></div>
      <div className="md-kpi"><span className="md-eyebrow">Đơn đã thanh toán</span><strong>{s.paidOrderCount}</strong>
        <small>trên {s.orderCount} đơn được tạo</small></div>
      <div className="md-kpi"><span className="md-eyebrow">Đã hoàn tiền</span><strong>{formatVnd(s.refundedAmount)}</strong>
        <small>{s.refundCount} khoản hoàn tất</small></div>
      <div className="md-kpi"><span className="md-eyebrow">Doanh thu thuần</span><strong>{formatVnd(s.grossRevenue - s.refundedAmount)}</strong>
        <small>doanh thu đã thu trừ hoàn tiền</small></div>
    </div>
    <div className="md-split">
      <div className="lg-panel">
        <div className="md-panel-head"><div><div className="md-eyebrow">{days} ngày gần nhất</div><h2>Doanh thu theo ngày</h2></div>
          <select className="md-select" aria-label="Khoảng thời gian" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            <option value={7}>7 ngày</option><option value={30}>30 ngày</option><option value={90}>90 ngày</option>
          </select></div>
        <div className="md-chart" role="img" aria-label="Biểu đồ doanh thu theo ngày">
          {s.daily.map((d) => <div key={d.date} title={`${d.date}: ${formatVnd(d.grossRevenue)}`}
            className={`md-bar${d.grossRevenue > 0 ? '' : ' empty'}`}
            style={{ height: max > 0 && d.grossRevenue > 0 ? `${Math.max(3, (d.grossRevenue / max) * 100)}%` : '2px' }} />)}
        </div>
        <div className="md-chart-axis"><span>{s.from}</span><span>{s.to}</span></div>
      </div>
      <div className="lg-panel">
        <div className="md-eyebrow">Cần xử lý</div><h2 className="md-title">Việc đang chờ</h2>
        <div className="md-row"><button type="button" onClick={() => onOpen('returns')}>Yêu cầu trả hàng chờ quyết định</button><b>{p.returnRequestsToDecide}</b></div>
        <div className="md-row"><button type="button" onClick={() => onOpen('refunds')}>Khoản hoàn tiền chờ xử lý</button><b>{p.pendingRefunds}</b></div>
        <div className="md-row"><button type="button" onClick={() => onOpen('refunds')}>Đơn QR hoàn về kho chưa hoàn tiền</button><b>{p.paidQrReturnedWithoutRefund}</b></div>
        <div className="md-row"><span>Ticket mới chưa có người nhận</span><b>{p.unassignedOpenTickets}</b></div>
        <div className="md-row"><span>Đơn giao thất bại</span><b>{p.deliveryFailedOrders}</b></div>
      </div>
    </div>
    <div className="md-split">
      <div className="lg-panel"><div className="md-eyebrow">Hiệu quả sản phẩm</div><h2 className="md-title">Sản phẩm bán chạy</h2>
        {data.topProducts.length === 0 ? <div className="lg-empty">Chưa có đơn hàng trong khoảng thời gian này.</div>
          : <div className="lg-tablewrap"><table className="lg-table"><thead><tr><th>Sản phẩm</th><th className="num">Số lượng</th><th className="num">Doanh thu</th></tr></thead>
            <tbody>{data.topProducts.map((t) => <tr key={t.productId}><td>{t.name}<small>{t.sku}</small></td>
              <td className="num">{t.unitsSold}</td><td className="num">{formatVnd(t.revenue)}</td></tr>)}</tbody></table></div>}
      </div>
      <div className="lg-panel"><div className="md-eyebrow">Cần chú ý</div><h2 className="md-title">Sắp hết hàng</h2>
        {data.lowStock.length === 0 ? <div className="lg-empty">Không có sản phẩm dưới ngưỡng tồn kho.</div>
          : data.lowStock.map((l) => <div className="md-row" key={l.productId}><span>{l.name}<small> · {l.sku}</small></span>
            <span className={`lg-chip ${l.available <= 0 ? 'lg-bad' : 'lg-warn'}`}>Còn {l.available}</span></div>)}
      </div>
    </div>
  </section>
}

function ReturnsPanel() {
  const [rows, setRows] = useState<ReturnRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<number | null>(null)
  const [notes, setNotes] = useState<Record<number, string>>({})
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setRows((await listReturns()).items) } catch (e) { setError(errorMessage(e)) } finally { setLoading(false) }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function decide(row: ReturnRequest, approved: boolean) {
    const note = (notes[row.id] ?? '').trim()
    if (!approved && !note) { setError(MSG_REJECT_NOTE); return }
    setBusy(row.id); setError('')
    try {
      await decideReturn(row.id, approved, note)
      setNotes((current) => ({ ...current, [row.id]: '' }))
      await load()
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(null) }
  }
  return <section>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    <div className="lg-toolbar"><span>{rows.length} yêu cầu trong danh sách</span><button type="button" className="lg-btn ghost" onClick={() => void load()}>Làm mới</button></div>
    {loading ? <div className="lg-empty">Đang tải yêu cầu…</div> : rows.length === 0 ? <div className="lg-empty">Chưa có yêu cầu trả hàng.</div> : rows.map((r) => <article className="lg-panel" key={r.id}>
      <div className="lg-toolbar"><strong>#{r.id} · {r.orderCode}</strong>
        <span className={`lg-chip ${returnStatusTone(r.status)}`}>{returnStatusLabel(r.status)}</span>
        <span>{formatDateTime(r.createdAt)}</span></div>
      <p><strong>{r.customerName}</strong> · {r.requestType === 'REFUND_ONLY' ? 'Chỉ hoàn tiền' : 'Trả hàng và hoàn tiền'}</p>
      <p>{r.reason}</p>
      {r.evidenceUrl && <p><a href={r.evidenceUrl} target="_blank" rel="noreferrer">Xem minh chứng</a></p>}
      <ul>{r.items.map((i) => <li key={i.id}>{i.productName} ({i.sku}) × {i.requestedQuantity} · {formatVnd(i.refundAmount)}
        {i.inspectionResult && <small>Kiểm tra: {inspectionResultLabel(i.inspectionResult)}; nhận {i.receivedQuantity}; nhập kho {i.restockQuantity}</small>}</li>)}</ul>
      {r.decisionNote && <p><small>Ghi chú: {r.decisionNote}</small></p>}
      {(r.status === 'REQUESTED' || r.status === 'UNDER_REVIEW' || r.status === 'INSPECTED') && <>
        <textarea className="md-note" aria-label={`Ghi chú quyết định cho yêu cầu #${r.id}`} maxLength={1000}
          placeholder="Ghi chú quyết định (bắt buộc khi từ chối)" value={notes[r.id] ?? ''}
          onChange={(e) => setNotes((current) => ({ ...current, [r.id]: e.target.value }))} />
        <div className="lg-actions">
          <button type="button" className="lg-btn primary" disabled={busy === r.id} onClick={() => void decide(r, true)}>
            {r.requestType === 'RETURN_AND_REFUND' && r.status !== 'INSPECTED' ? 'Cho phép trả hàng' : 'Duyệt hoàn tiền'}
          </button>
          <button type="button" className="lg-btn danger" disabled={busy === r.id} onClick={() => void decide(r, false)}>Từ chối</button>
        </div></>}
    </article>)}
  </section>
}

function RefundsPanel() {
  const [rows, setRows] = useState<Refund[]>([])
  const [failed, setFailed] = useState<FailedDeliveryOrder[]>([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const load = useCallback(async () => {
    try {
      const [refunds, failedOrders] = await Promise.all([listRefunds(), listFailedDeliveryOrders()])
      setRows(refunds.items); setFailed(failedOrders); setError('')
    } catch (e) { setError(errorMessage(e)) }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function complete(id: number) {
    setBusy(`refund-${id}`); setError(''); setNotice('')
    try { await completeRefund(id); setNotice('Đã ghi nhận hoàn tiền hoàn tất.'); await load() } catch (e) { setError(errorMessage(e)) } finally { setBusy(null) }
  }
  async function createRefund(order: FailedDeliveryOrder) {
    setBusy(`order-${order.orderId}`); setError(''); setNotice('')
    try { await createFailedDeliveryRefund(order.orderId); setNotice(`Đã tạo khoản hoàn tiền cho đơn ${order.orderCode}.`); await load() }
    catch (e) { setError(errorMessage(e)) } finally { setBusy(null) }
  }
  return <section>
    <div className="lg-msg">Thanh toán của dự án hiện đang mô phỏng. Nút xác nhận chỉ ghi nhận hoàn tiền đã được xử lý bên ngoài; hệ thống không chuyển tiền thật.</div>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    {notice && <div className="lg-msg ok" role="status">{notice}</div>}
    {failed.length > 0 && <div className="lg-panel"><h3>Đơn QR hoàn về kho chưa hoàn tiền</h3>
      <div className="lg-tablewrap"><table className="lg-table"><thead><tr><th>Đơn hàng</th><th>Khách hàng</th><th>Hoàn về kho</th><th className="num">Đã thanh toán</th><th /></tr></thead>
        <tbody>{failed.map((o) => <tr key={o.orderId}><td>{o.orderCode}</td><td>{o.customerName}</td><td>{formatDateTime(o.returnedAt)}</td>
          <td className="num">{formatVnd(o.amount)}</td>
          <td><button type="button" className="lg-btn primary" disabled={busy === `order-${o.orderId}`}
            onClick={() => void createRefund(o)}>Tạo hoàn tiền</button></td></tr>)}</tbody></table></div></div>}
    <div className="lg-tablewrap"><table className="lg-table"><thead><tr><th>Mã hoàn tiền</th><th>Đơn hàng</th><th>Lý do</th><th>Phương thức</th><th className="num">Số tiền</th><th>Trạng thái</th><th /></tr></thead>
      <tbody>{rows.map((r) => <tr key={r.id}><td>#{r.id}</td><td>{r.orderCode}</td><td>{refundReasonLabel(r.reason)}</td>
        <td>{refundMethodLabel(r.method)}</td><td className="num">{formatVnd(r.amount)}</td>
        <td><span className={`lg-chip ${refundStatusTone(r.status)}`}>{refundStatusLabel(r.status)}</span></td>
        <td><button type="button" className="lg-btn primary" disabled={busy === `refund-${r.id}`}
          onClick={() => void complete(r.id)}>Ghi nhận hoàn tất</button></td></tr>)}</tbody></table>
      {rows.length === 0 && <div className="lg-empty">Không có khoản hoàn tiền đang chờ.</div>}</div>
  </section>
}

function StaffReportsPanel() {
  const [date, setDate] = useState(vnDate())
  const [rows, setRows] = useState<StaffReportRow[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setRows(await listStaffDailyReports(date)) } catch (e) { setError(errorMessage(e)) } finally { setLoading(false) }
  }, [date])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  return <section>
    <div className="lg-toolbar"><label>Ngày báo cáo <input type="date" value={date} max={vnDate()} onChange={(e) => setDate(e.target.value)} /></label>
      <span>{rows.length} báo cáo</span>
      <button type="button" className="lg-btn ghost" onClick={() => void load()}>Làm mới</button></div>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    {loading ? <div className="lg-empty">Đang tải báo cáo…</div> : rows.length === 0
      ? <div className="lg-empty">Không có báo cáo hằng ngày cho ngày đã chọn.</div>
      : rows.map((r) => <article className="lg-panel" key={`${r.staffName}-${r.updatedAt}`}>
        <div className="lg-toolbar"><strong>{r.staffName}</strong>
          <span className="lg-chip lg-info">{STAFF_ROLE[r.role] ?? r.role}</span>
          <span>Cập nhật {formatDateTime(r.updatedAt)}</span></div>
        <p className="md-report">{r.content}</p>
      </article>)}
  </section>
}

function ReportsPanel() {
  const [from, setFrom] = useState(vnDate(-6))
  const [to, setTo] = useState(vnDate())
  const [data, setData] = useState<ReportSummary | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)
  const load = useCallback(async () => {
    setLoading(true); setError(''); setNotice('')
    try { setData(await getTransactionReport(from, to)) } catch (e) { setError(errorMessage(e)) } finally { setLoading(false) }
  }, [from, to])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function exportCsv() {
    setError(''); setNotice('')
    try { await downloadTransactionReport(from, to); setNotice('Đã xuất báo cáo thành công.') } catch (e) { setError(errorMessage(e)) }
  }
  const empty = data !== null && data.orderCount === 0 && data.paidOrderCount === 0 && data.refundCount === 0
  return <section>
    <div className="lg-toolbar"><label>Từ ngày <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
      <label>Đến ngày <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
      <button type="button" className="lg-btn ghost" onClick={() => void load()}>Xem báo cáo</button>
      <button type="button" className="lg-btn primary" disabled={!data} onClick={() => void exportCsv()}>Xuất CSV</button></div>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    {notice && <div className="lg-msg ok" role="status">{notice}</div>}
    {empty && <div className="lg-msg" role="status">Khoảng thời gian này chưa có giao dịch; file CSV sẽ chỉ có các dòng bằng 0.</div>}
    {loading ? <div className="lg-empty">Đang tổng hợp giao dịch…</div> : data && <>
      <div className="lg-split">
        <div className="lg-panel"><h3>Tổng quan giao dịch</h3>
          <div className="lg-summary-row"><strong>Tổng đơn hàng</strong><b>{data.orderCount}</b></div>
          <div className="lg-summary-row"><strong>Đơn đã thanh toán</strong><b>{data.paidOrderCount}</b></div>
          <div className="lg-summary-row"><strong>Doanh thu đã thu</strong><b>{formatVnd(data.grossRevenue)}</b></div>
          <div className="lg-summary-row"><strong>Đã hoàn tiền</strong><b>{formatVnd(data.refundedAmount)}</b></div>
          <div className="lg-summary-row"><strong>Số lần hoàn tiền</strong><b>{data.refundCount}</b></div>
        </div>
        <div className="lg-panel"><h3>Định nghĩa số liệu</h3><p>Số đơn được nhóm theo ngày tạo đơn. Doanh thu đã thu và số đơn thanh toán được nhóm theo thời điểm QR thanh toán thành công hoặc COD được thu. Hoàn tiền chỉ tính các giao dịch đã hoàn tất.</p><p>Múi giờ báo cáo: Việt Nam (UTC+7).</p></div>
      </div>
      <div className="lg-tablewrap"><table className="lg-table"><thead><tr><th>Ngày</th><th className="num">Đơn</th><th className="num">Đã thanh toán</th><th className="num">Doanh thu đã thu</th><th className="num">Hoàn tiền</th><th className="num">Lượt hoàn</th></tr></thead>
        <tbody>{data.daily.map((d) => <tr key={d.date}><td>{d.date}</td><td className="num">{d.orderCount}</td><td className="num">{d.paidOrderCount}</td><td className="num">{formatVnd(d.grossRevenue)}</td><td className="num">{formatVnd(d.refundedAmount)}</td><td className="num">{d.refundCount}</td></tr>)}</tbody></table></div>
    </>}
  </section>
}
