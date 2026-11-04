import { useCallback, useEffect, useState, type FormEvent } from 'react'
import AccountLayout from './AccountLayout'
import { createTicket, listMyTickets, replyTicket, type SupportTicket } from './api/afterSales'
import { errorMessage } from './api/warehouseDelivery'
import { formatDateTime } from './logisticsUi'
import './logistics.css'

export default function CustomerSupportPage() {
  const [tickets,setTickets]=useState<SupportTicket[]>([])
  const [subject,setSubject]=useState(''); const [orderId,setOrderId]=useState(''); const [message,setMessage]=useState('')
  const [replies,setReplies]=useState<Record<number,string>>({})
  const [error,setError]=useState(''); const [notice,setNotice]=useState(''); const [busy,setBusy]=useState(false)
  const load=useCallback(async()=>{try{setTickets(await listMyTickets())}catch(e){setError(errorMessage(e))}},[])
  useEffect(()=>{void load()},[load])
  async function submit(event:FormEvent){event.preventDefault();setBusy(true);setError('');setNotice('')
    try{await createTicket({subject,orderId:orderId.trim()?Number(orderId):null,message});setSubject('');setOrderId('');setMessage('');setNotice('Đã gửi yêu cầu hỗ trợ.');await load()}
    catch(e){setError(errorMessage(e))}finally{setBusy(false)}}
  async function sendReply(id:number){const text=replies[id]?.trim();if(!text)return;setBusy(true);setError('')
    try{await replyTicket(id,text);setReplies({...replies,[id]:''});await load()}catch(e){setError(errorMessage(e))}finally{setBusy(false)}}
  return <AccountLayout title="Hỗ trợ khách hàng" intro="Gửi câu hỏi và theo dõi trao đổi với đội ngũ hỗ trợ.">
    {error&&<p className="auth-error" role="alert">{error}</p>}{notice&&<p className="auth-notice" role="status">{notice}</p>}
    <form className="lg-panel" onSubmit={submit}><h3>Tạo yêu cầu hỗ trợ</h3>
      <div className="lg-field"><label htmlFor="ticket-subject">Tiêu đề</label><input id="ticket-subject" required maxLength={200} value={subject} onChange={e=>setSubject(e.target.value)}/></div>
      <div className="lg-field"><label htmlFor="ticket-order">Mã ID đơn hàng (không bắt buộc)</label><input id="ticket-order" type="number" min={1} value={orderId} onChange={e=>setOrderId(e.target.value)}/></div>
      <div className="lg-field"><label htmlFor="ticket-message">Nội dung</label><textarea id="ticket-message" required maxLength={5000} rows={4} value={message} onChange={e=>setMessage(e.target.value)}/></div>
      <button className="lg-btn primary" disabled={busy}>Gửi yêu cầu</button>
    </form>
    <h2>Yêu cầu của bạn</h2>
    {tickets.map(t=><article className="lg-panel" key={t.id}><div className="lg-toolbar"><strong>#{t.id} · {t.subject}</strong><span className="lg-chip lg-info">{t.status}</span><small>{t.orderCode??'Không gắn đơn hàng'}</small></div>
      <div>{t.messages.map(m=><p key={m.id}><strong>{m.senderName}</strong> · {formatDateTime(m.createdAt)}<br/>{m.message}</p>)}</div>
      {t.status!=='CLOSED'&&<div className="lg-field"><label htmlFor={`reply-${t.id}`}>Phản hồi</label><textarea id={`reply-${t.id}`} rows={2} value={replies[t.id]??''} onChange={e=>setReplies({...replies,[t.id]:e.target.value})}/>
        <button className="lg-btn ghost" disabled={busy||!replies[t.id]?.trim()} onClick={()=>void sendReply(t.id)}>Gửi phản hồi</button></div>}
    </article>)}
    {tickets.length===0&&<div className="lg-empty">Bạn chưa có yêu cầu hỗ trợ nào.</div>}
  </AccountLayout>
}
