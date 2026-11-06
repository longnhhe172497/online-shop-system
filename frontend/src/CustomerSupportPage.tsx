import { useCallback, useEffect, useState, type FormEvent } from 'react'
import AccountLayout from './AccountLayout'
import { createTicket, listMyTickets, replyTicket, type SupportTicket } from './api/afterSales'
import { errorMessage } from './api/warehouseDelivery'
import { ticketStatusLabel, ticketStatusTone } from './afterSalesUi'
import { formatDateTime } from './logisticsFormat'
import './logistics.css'

export default function CustomerSupportPage() {
  const [tickets, setTickets] = useState<SupportTicket[]>([])
  const [subject, setSubject] = useState('')
  const [orderId, setOrderId] = useState('')
  const [message, setMessage] = useState('')
  const [replies, setReplies] = useState<Record<number, string>>({})
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try { setTickets(await listMyTickets()) }
    catch (failure) { setError(errorMessage(failure)) }
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('')
    try {
      await createTicket({ subject, orderId: orderId.trim() ? Number(orderId) : null, message })
      setSubject(''); setOrderId(''); setMessage('')
      setNotice('Đã gửi yêu cầu hỗ trợ. Đội ngũ FORME sẽ phản hồi trong mục này.')
      await load()
    } catch (failure) { setError(errorMessage(failure)) }
    finally { setBusy(false) }
  }

  async function sendReply(id: number) {
    const text = replies[id]?.trim()
    if (!text) return
    setBusy(true); setError(''); setNotice('')
    try {
      await replyTicket(id, text)
      setReplies((current) => ({ ...current, [id]: '' }))
      setNotice('Đã gửi phản hồi.')
      await load()
    } catch (failure) { setError(errorMessage(failure)) }
    finally { setBusy(false) }
  }

  return <AccountLayout title="Trung tâm hỗ trợ" intro="Gửi câu hỏi và theo dõi trao đổi với đội ngũ FORME.">
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}

    <form className="lg-panel support-create" onSubmit={submit}>
      <div className="support-section-head"><div><p className="card-eyebrow">YÊU CẦU MỚI</p>
        <h2>Chúng tôi có thể giúp gì cho bạn?</h2></div>
        <p>Điền đủ thông tin để đội ngũ hỗ trợ xử lý nhanh hơn.</p></div>
      <div className="support-form-grid">
        <div className="lg-field"><label htmlFor="ticket-subject">Tiêu đề</label>
          <input id="ticket-subject" required maxLength={200} value={subject}
            placeholder="Ví dụ: Cần hỗ trợ đơn hàng"
            onChange={(event) => setSubject(event.target.value)} /></div>
        <div className="lg-field"><label htmlFor="ticket-order">Mã số đơn hàng (không bắt buộc)</label>
          <input id="ticket-order" type="number" min={1} value={orderId} placeholder="Nhập mã số đơn hàng"
            onChange={(event) => setOrderId(event.target.value)} /></div>
        <div className="lg-field support-message-field"><label htmlFor="ticket-message">Nội dung</label>
          <textarea id="ticket-message" required maxLength={5000} rows={5} value={message}
            placeholder="Mô tả chi tiết vấn đề bạn đang gặp…"
            onChange={(event) => setMessage(event.target.value)} /></div>
      </div>
      <button type="submit" className="lg-btn primary" disabled={busy}>
        {busy ? 'Đang gửi…' : 'Gửi yêu cầu hỗ trợ'}</button>
    </form>

    <div className="support-list-head"><div><p className="card-eyebrow">LỊCH SỬ HỖ TRỢ</p>
      <h2>Yêu cầu của bạn</h2></div><span>{tickets.length} yêu cầu</span></div>
    <div className="support-ticket-list">
      {tickets.map((ticket) => <article className="lg-panel support-ticket" key={ticket.id}>
        <header className="support-ticket-head"><div><strong>#{ticket.id} · {ticket.subject}</strong>
          <small>{ticket.orderCode ?? 'Không gắn với đơn hàng'}</small></div>
          <span className={`lg-chip ${ticketStatusTone(ticket.status)}`}>{ticketStatusLabel(ticket.status)}</span></header>
        <div className="support-thread">
          {ticket.messages.map((item) => <div className="support-message" key={item.id}>
            <div className="support-message-meta"><strong>{item.senderName}</strong><time>{formatDateTime(item.createdAt)}</time></div>
            <p>{item.message}</p></div>)}
        </div>
        {ticket.status !== 'CLOSED' && <div className="support-compose">
          <div className="lg-field"><label htmlFor={`reply-${ticket.id}`}>Phản hồi</label>
            <textarea id={`reply-${ticket.id}`} rows={3} value={replies[ticket.id] ?? ''}
              placeholder="Nhập phản hồi của bạn…"
              onChange={(event) => setReplies((current) => ({ ...current, [ticket.id]: event.target.value }))} /></div>
          <button type="button" className="lg-btn ghost" disabled={busy || !replies[ticket.id]?.trim()}
            onClick={() => void sendReply(ticket.id)}>Gửi phản hồi</button></div>}
      </article>)}
      {tickets.length === 0 && <div className="lg-empty support-empty"><strong>Chưa có yêu cầu hỗ trợ</strong>
        <span>Khi bạn gửi yêu cầu mới, lịch sử trao đổi sẽ xuất hiện tại đây.</span></div>}
    </div>
  </AccountLayout>
}
