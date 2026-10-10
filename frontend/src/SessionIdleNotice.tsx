import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { getMe, logout } from './api/auth'
import { getUiError } from './uiError'

const WARNING_AFTER_MS = 25 * 60 * 1000
const EXPIRE_AFTER_MS = 30 * 60 * 1000

export default function SessionIdleNotice() {
  const location = useLocation()
  const navigate = useNavigate()
  const protectedRoute = location.pathname.startsWith('/me') || location.pathname.startsWith('/internal')
  const [active, setActive] = useState(false)
  const [warning, setWarning] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const lastServerActivity = useRef(0)

  useEffect(() => {
    if (!protectedRoute) return
    let mounted = true
    getMe().then(() => { if (mounted) { lastServerActivity.current = Date.now(); setActive(true) } })
      .catch(() => { if (mounted) setActive(false) })
    return () => { mounted = false }
  }, [protectedRoute])

  useEffect(() => {
    if (!active || !protectedRoute) return
    const recordActivity = () => { lastServerActivity.current = Date.now(); setWarning(false) }
    window.addEventListener('forme:session-activity', recordActivity)
    const timer = window.setInterval(() => {
      const elapsed = Date.now() - lastServerActivity.current
      if (elapsed >= EXPIRE_AFTER_MS) {
        setActive(false); setWarning(false)
        navigate('/auth', { replace: true, state: { notice: 'Phiên đăng nhập đã hết hạn do không hoạt động.' } })
      } else if (elapsed >= WARNING_AFTER_MS) setWarning(true)
    }, 15_000)
    return () => { window.clearInterval(timer)
      window.removeEventListener('forme:session-activity', recordActivity) }
  }, [active, protectedRoute, navigate])

  async function continueSession() {
    setBusy(true); setError('')
    try { await getMe(); lastServerActivity.current = Date.now(); setWarning(false) }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function signOut() {
    setBusy(true)
    await logout().catch(() => {})
    setActive(false); setWarning(false)
    navigate('/auth', { replace: true, state: { notice: 'Đã đăng xuất.' } })
  }

  if (!active || !warning || !protectedRoute) return null
  return <div className="address-modal-backdrop"><section className="address-modal confirm-modal" role="alertdialog"
    aria-modal="true" aria-labelledby="session-idle-title">
    <p className="card-eyebrow">BẢO MẬT PHIÊN</p><h2 id="session-idle-title">Phiên sắp hết hạn</h2>
    <p>Phiên không có hoạt động trên hệ thống gần 25 phút. Phiên sẽ hết hạn sau khoảng 5 phút nữa.</p>
    {error && <p className="auth-error" role="alert">{error}</p>}
    <div className="address-modal-actions"><button className="account-secondary" disabled={busy}
      onClick={() => void signOut()}>Đăng xuất</button>
      <button className="account-primary" disabled={busy} onClick={() => void continueSession()}>
        Tiếp tục phiên</button></div>
  </section></div>
}
