import { useCallback, useEffect, useState } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import { errorMessage } from './api/warehouseDelivery'
import { listTicketQueue, replyTicket, updateTicketStatus, type SupportTicket } from './api/afterSales'
import { ticketStatusLabel, ticketStatusTone } from './afterSalesUi'
import { formatDateTime } from './logisticsFormat'
import './logistics.css'

type Tab = 'queue'
const NAV: StaffTab<Tab>[] = [{ id: 'queue', label: 'Yêu cầu hỗ trợ' }]

export default function SupportTicketsPage() {
  return <StaffLayout eyebrow="Chăm sóc khách hàng" title="Hỗ trợ khách hàng"
    subtitle="Tiếp nhận, phản hồi và theo dõi yêu cầu hỗ trợ của khách hàng."
    roleName="Nhân viên hỗ trợ" tabs={NAV} active="queue" onNavigate={() => {}}>
    <Queue />
  </StaffLayout>
}

function Queue() {
  const [rows, setRows] = useState<SupportTicket[]>([])
  const [drafts, setDrafts] = useState<Record<number, string>>({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<number | null>(null)

  const load = useCallback(async () => {
    try { setRows((await listTicketQueue()).items); setError('') }
    catch (failure) { setError(errorMessage(failure)) }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function reply(id: number) {
    const message = drafts[id]?.trim()
    if (!message) return
    setBusy(id); setError('')
    try {
      await replyTicket(id, message)
      setDrafts((current) => ({ ...current, [id]: '' }))
      await load()
    } catch (failure) { setError(errorMessage(failure)) }
    finally { setBusy(null) }
  }

  async function changeStatus(id: number, value: 'RESOLVED' | 'CLOSED') {
    setBusy(id); setError('')
    try { await updateTicketStatus(id, value); await load() }
    catch (failure) { setError(errorMessage(failure)) }
    finally { setBusy(null) }
  }

  return <section>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    <div className="lg-toolbar support-queue-toolbar"><span><strong>{rows.length}</strong> yêu cầu đang hiển thị</span>
      <button type="button" className="lg-btn ghost" onClick={() => void load()}>Làm mới</button></div>
    <div className="support-ticket-list">
      {rows.map((ticket) => <article className="lg-panel support-ticket" key={ticket.id}>
        <header className="support-ticket-head"><div><strong>#{ticket.id} · {ticket.subject}</strong>
          <small>{ticket.customerName} · {ticket.orderCode ?? 'Không gắn với đơn hàng'}</small></div>
          <span className={`lg-chip ${ticketStatusTone(ticket.status)}`}>{ticketStatusLabel(ticket.status)}</span></header>
        <div className="support-thread">
          {ticket.messages.map((item) => <div className="support-message" key={item.id}>
            <div className="support-message-meta"><strong>{item.senderName}</strong><time>{formatDateTime(item.createdAt)}</time></div>
            <p>{item.message}</p></div>)}
        </div>
        {ticket.status !== 'CLOSED' && <div className="support-compose">
          <div className="lg-field"><label htmlFor={`staff-reply-${ticket.id}`}>Trả lời khách hàng</label>
            <textarea id={`staff-reply-${ticket.id}`} rows={3} value={drafts[ticket.id] ?? ''}
              placeholder="Nhập nội dung phản hồi…"
              onChange={(event) => setDrafts((current) => ({ ...current, [ticket.id]: event.target.value }))} /></div>
          <div className="lg-actions"><button type="button" className="lg-btn primary"
            disabled={busy === ticket.id || !drafts[ticket.id]?.trim()} onClick={() => void reply(ticket.id)}>Gửi trả lời</button>
            {ticket.status !== 'RESOLVED' && <button type="button" className="lg-btn ghost" disabled={busy === ticket.id}
              onClick={() => void changeStatus(ticket.id, 'RESOLVED')}>Đánh dấu đã giải quyết</button>}
            <button type="button" className="lg-btn danger" disabled={busy === ticket.id}
              onClick={() => void changeStatus(ticket.id, 'CLOSED')}>Đóng yêu cầu</button></div>
        </div>}
      </article>)}
      {rows.length === 0 && <div className="lg-empty support-empty"><strong>Không có yêu cầu cần xử lý</strong>
        <span>Các yêu cầu mới của khách hàng sẽ xuất hiện tại đây.</span></div>}
    </div>
  </section>
}
