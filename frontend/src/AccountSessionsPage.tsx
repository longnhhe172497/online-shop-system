import { useEffect, useState } from 'react'
import { listSessions, revokeOtherSessions, revokeSession, type Session } from './api/profile'
import { getUiError } from './uiError'
import AccountLayout from './AccountLayout'

export default function AccountSessionsPage() {
  const [sessions, setSessions] = useState<Session[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => { let active = true
    listSessions().then((data) => { if (active) setSessions(data) })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  async function remove(id: number) {
    setBusy(true); setError('')
    try { await revokeSession(id); setSessions((current) => current.filter((item) => item.id !== id))
      setNotice('Đã kết thúc phiên đăng nhập.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  async function removeOthers() {
    setBusy(true); setError('')
    try { await revokeOtherSessions(); setSessions((current) => current.filter((item) => item.current))
      setNotice('Đã đăng xuất các thiết bị khác.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  return <AccountLayout title="Thiết bị đăng nhập" intro="Xem và kết thúc những phiên bạn không còn sử dụng.">
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}
    <section className="account-card sessions-panel"><div className="address-section-head"><div>
      <p className="card-eyebrow">PHIÊN HOẠT ĐỘNG</p><h2>Đăng nhập gần đây</h2>
      <p>{sessions.length} phiên đang hoạt động</p></div>
      <button className="account-secondary" disabled={busy || sessions.filter((s) => !s.current).length === 0}
        onClick={() => void removeOthers()}>Đăng xuất thiết bị khác</button></div>
      {loading ? <p>Đang tải danh sách thiết bị…</p> : sessions.length === 0 ?
        <p>Không có phiên đăng nhập nào đang hoạt động.</p> :
        <div className="session-list">{sessions.map((item) => <article className="session-row" key={item.id}>
          <div className="session-icon" aria-hidden="true">⌁</div><div><strong>{item.userAgent?.slice(0, 80) || 'Trình duyệt không xác định'}</strong>
            <p>{item.ipAddress || 'Không rõ IP'} · Đăng nhập {new Date(item.createdAt).toLocaleString('vi-VN')}</p>
            <small>Hoạt động gần nhất {new Date(item.lastSeenAt ?? item.createdAt).toLocaleString('vi-VN')}
              {' · '}Hết hạn {new Date(item.expiresAt).toLocaleString('vi-VN')}</small></div>
          {item.current ? <span className="security-state on">Thiết bị này</span> :
            <button className="admin-link" disabled={busy} onClick={() => void remove(item.id)}>Đăng xuất</button>}
        </article>)}</div>}
    </section>
  </AccountLayout>
}
