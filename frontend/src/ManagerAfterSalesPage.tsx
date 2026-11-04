import { useCallback, useEffect, useState } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import { formatDateTime, formatVnd } from './logisticsUi'
import { errorMessage } from './api/warehouseDelivery'
import {
  completeRefund, decideReturn, downloadTransactionReport, getTransactionReport,
  listRefunds, listReturns, type Refund, type ReportSummary, type ReturnRequest,
} from './api/afterSales'
import './logistics.css'

type Tab = 'returns' | 'refunds' | 'reports'
const NAV: StaffTab<Tab>[] = [
  { id: 'returns', label: 'Yêu cầu trả hàng' },
  { id: 'refunds', label: 'Hoàn tiền' },
  { id: 'reports', label: 'Báo cáo giao dịch' },
]
const today = () => new Date().toISOString().slice(0, 10)
const weekAgo = () => { const d = new Date(); d.setDate(d.getDate() - 6); return d.toISOString().slice(0, 10) }

export default function ManagerAfterSalesPage() {
  const [tab, setTab] = useState<Tab>('returns')
  return <StaffLayout eyebrow="Quản lý vận hành" title="Hậu mãi & báo cáo" roleName="Quản lý"
    tabs={NAV} active={tab} onNavigate={setTab}>
    {tab === 'returns' ? <ReturnsPanel /> : tab === 'refunds' ? <RefundsPanel /> : <ReportsPanel />}
  </StaffLayout>
}

function ReturnsPanel() {
  const [rows, setRows] = useState<ReturnRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<number | null>(null)
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setRows((await listReturns()).items) } catch (e) { setError(errorMessage(e)) } finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])
  async function decide(row: ReturnRequest, approved: boolean) {
    setBusy(row.id); setError('')
    try { await decideReturn(row.id, approved); await load() } catch (e) { setError(errorMessage(e)) } finally { setBusy(null) }
  }
  return <section>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    <div className="lg-toolbar"><span>{rows.length} yêu cầu trong danh sách</span><button className="lg-btn ghost" onClick={() => void load()}>Làm mới</button></div>
    {loading ? <div className="lg-empty">Đang tải yêu cầu…</div> : rows.length === 0 ? <div className="lg-empty">Chưa có yêu cầu trả hàng.</div> : rows.map((r) => <article className="lg-panel" key={r.id}>
      <div className="lg-toolbar"><strong>#{r.id} · {r.orderCode}</strong><span className="lg-chip lg-info">{r.status}</span><span>{formatDateTime(r.createdAt)}</span></div>
      <p><strong>{r.customerName}</strong> · {r.requestType === 'REFUND_ONLY' ? 'Chỉ hoàn tiền' : 'Trả hàng và hoàn tiền'}</p>
      <p>{r.reason}</p>
      {r.evidenceUrl && <p><a href={r.evidenceUrl} target="_blank" rel="noreferrer">Xem minh chứng</a></p>}
      <ul>{r.items.map((i) => <li key={i.id}>{i.productName} ({i.sku}) × {i.requestedQuantity} · {formatVnd(i.refundAmount)}
        {i.inspectionResult && <small>Kiểm tra: {i.inspectionResult}; nhận {i.receivedQuantity}; nhập kho {i.restockQuantity}</small>}</li>)}</ul>
      {r.decisionNote && <p><small>Ghi chú: {r.decisionNote}</small></p>}
      {(r.status === 'REQUESTED' || r.status === 'UNDER_REVIEW' || r.status === 'INSPECTED') && <div className="lg-actions">
        <button className="lg-btn primary" disabled={busy === r.id} onClick={() => void decide(r, true)}>
          {r.requestType === 'RETURN_AND_REFUND' && r.status !== 'INSPECTED' ? 'Cho phép trả hàng' : 'Duyệt hoàn tiền'}
        </button>
        <button className="lg-btn danger" disabled={busy === r.id} onClick={() => void decide(r, false)}>Từ chối</button>
      </div>}
    </article>)}
  </section>
}

function RefundsPanel() {
  const [rows, setRows] = useState<Refund[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<number | null>(null)
  const load = useCallback(async () => { try { setRows((await listRefunds()).items); setError('') } catch (e) { setError(errorMessage(e)) } }, [])
  useEffect(() => { void load() }, [load])
  async function complete(id: number) {
    setBusy(id); setError('')
    try { await completeRefund(id); await load() } catch (e) { setError(errorMessage(e)) } finally { setBusy(null) }
  }
  return <section>
    <div className="lg-msg">Thanh toán của dự án hiện đang mô phỏng. Nút xác nhận chỉ ghi nhận hoàn tiền đã được xử lý bên ngoài; hệ thống không chuyển tiền thật.</div>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    <div className="lg-tablewrap"><table className="lg-table"><thead><tr><th>Mã hoàn tiền</th><th>Đơn hàng</th><th>Lý do</th><th>Phương thức</th><th className="num">Số tiền</th><th>Trạng thái</th><th /></tr></thead>
      <tbody>{rows.map((r) => <tr key={r.id}><td>#{r.id}</td><td>{r.orderCode}</td><td>{r.reason}</td><td>{r.method}</td><td className="num">{formatVnd(r.amount)}</td><td>{r.status}</td>
        <td><button className="lg-btn primary" disabled={busy === r.id} onClick={() => void complete(r.id)}>Ghi nhận hoàn tất</button></td></tr>)}</tbody></table>
      {rows.length === 0 && <div className="lg-empty">Không có khoản hoàn tiền đang chờ.</div>}</div>
  </section>
}

function ReportsPanel() {
  const [from, setFrom] = useState(weekAgo())
  const [to, setTo] = useState(today())
  const [data, setData] = useState<ReportSummary | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setData(await getTransactionReport(from, to)) } catch (e) { setError(errorMessage(e)) } finally { setLoading(false) }
  }, [from, to])
  useEffect(() => { void load() }, [load])
  return <section>
    <div className="lg-toolbar"><label>Từ ngày <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
      <label>Đến ngày <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
      <button className="lg-btn ghost" onClick={() => void load()}>Xem báo cáo</button>
      <button className="lg-btn primary" disabled={!data} onClick={() => void downloadTransactionReport(from, to)}>Xuất CSV</button></div>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    {loading ? <div className="lg-empty">Đang tổng hợp giao dịch…</div> : data && <>
      <div className="lg-split">
        <div className="lg-panel"><h3>Tổng quan giao dịch</h3>
          <div className="lg-summary-row"><strong>Tổng đơn hàng</strong><b>{data.orderCount}</b></div>
          <div className="lg-summary-row"><strong>Đơn đã thanh toán</strong><b>{data.paidOrderCount}</b></div>
          <div className="lg-summary-row"><strong>Doanh thu đã thu</strong><b>{formatVnd(data.grossRevenue)}</b></div>
          <div className="lg-summary-row"><strong>Đã hoàn tiền</strong><b>{formatVnd(data.refundedAmount)}</b></div>
          <div className="lg-summary-row"><strong>Số lần hoàn tiền</strong><b>{data.refundCount}</b></div>
        </div>
        <div className="lg-panel"><h3>Định nghĩa số liệu</h3><p>Số đơn được nhóm theo ngày tạo đơn. Doanh thu đã thu và số đơn thanh toán được nhóm theo thời điểm QR thanh toán thành công hoặc COD được thu. Hoàn tiền chỉ tính bản ghi COMPLETED.</p><p>Thời gian báo cáo: Asia/Ho_Chi_Minh.</p></div>
      </div>
      <div className="lg-tablewrap"><table className="lg-table"><thead><tr><th>Ngày</th><th className="num">Đơn</th><th className="num">Đã thanh toán</th><th className="num">Doanh thu đã thu</th><th className="num">Hoàn tiền</th><th className="num">Lượt hoàn</th></tr></thead>
        <tbody>{data.daily.map((d) => <tr key={d.date}><td>{d.date}</td><td className="num">{d.orderCount}</td><td className="num">{d.paidOrderCount}</td><td className="num">{formatVnd(d.grossRevenue)}</td><td className="num">{formatVnd(d.refundedAmount)}</td><td className="num">{d.refundCount}</td></tr>)}</tbody></table></div>
    </>}
  </section>
}
