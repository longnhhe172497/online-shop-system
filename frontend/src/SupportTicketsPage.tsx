import { useCallback, useEffect, useState } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import { errorMessage } from './api/warehouseDelivery'
import { listTicketQueue, replyTicket, updateTicketStatus, type SupportTicket } from './api/afterSales'
import { formatDateTime } from './logisticsUi'
import './logistics.css'

type Tab='queue'; const NAV:StaffTab<Tab>[]=[{id:'queue',label:'Ticket hỗ trợ'}]
export default function SupportTicketsPage(){return <StaffLayout eyebrow="Chăm sóc khách hàng" title="Hỗ trợ khách hàng" subtitle="Tiếp nhận, phản hồi và theo dõi yêu cầu hỗ trợ của khách hàng." roleName="Nhân viên hỗ trợ" tabs={NAV} active="queue" onNavigate={()=>{}}><Queue/></StaffLayout>}
function Queue(){
 const [rows,setRows]=useState<SupportTicket[]>([]);const [drafts,setDrafts]=useState<Record<number,string>>({});const [error,setError]=useState('');const [busy,setBusy]=useState<number|null>(null)
 const load=useCallback(async()=>{try{setRows((await listTicketQueue()).items);setError('')}catch(e){setError(errorMessage(e))}},[])
 useEffect(()=>{void load()},[load])
 async function reply(id:number){const message=drafts[id]?.trim();if(!message)return;setBusy(id);setError('');try{await replyTicket(id,message);setDrafts({...drafts,[id]:''});await load()}catch(e){setError(errorMessage(e))}finally{setBusy(null)}}
 async function status(id:number,value:'RESOLVED'|'CLOSED'){setBusy(id);setError('');try{await updateTicketStatus(id,value);await load()}catch(e){setError(errorMessage(e))}finally{setBusy(null)}}
 return <section>{error&&<div className="lg-msg err" role="alert">{error}</div>}<div className="lg-toolbar"><span>{rows.length} ticket</span><button className="lg-btn ghost" onClick={()=>void load()}>Làm mới</button></div>
 {rows.map(t=><article className="lg-panel" key={t.id}><div className="lg-toolbar"><strong>#{t.id} · {t.subject}</strong><span className="lg-chip lg-info">{t.status}</span><small>{t.customerName} · {t.orderCode??'Không gắn đơn'}</small></div>
 {t.messages.map(m=><p key={m.id}><strong>{m.senderName}</strong> · {formatDateTime(m.createdAt)}<br/>{m.message}</p>)}
 {t.status!=='CLOSED'&&<><div className="lg-field"><label htmlFor={`staff-reply-${t.id}`}>Trả lời khách hàng</label><textarea id={`staff-reply-${t.id}`} rows={2} value={drafts[t.id]??''} onChange={e=>setDrafts({...drafts,[t.id]:e.target.value})}/></div>
 <div className="lg-actions"><button className="lg-btn primary" disabled={busy===t.id||!drafts[t.id]?.trim()} onClick={()=>void reply(t.id)}>Gửi trả lời</button>
 {t.status!=='RESOLVED'&&<button className="lg-btn ghost" disabled={busy===t.id} onClick={()=>void status(t.id,'RESOLVED')}>Đánh dấu đã giải quyết</button>}
 <button className="lg-btn danger" disabled={busy===t.id} onClick={()=>void status(t.id,'CLOSED')}>Đóng ticket</button></div></>}
 </article>)}{rows.length===0&&<div className="lg-empty">Không có ticket cần xử lý.</div>}</section>
}
