import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { acceptInvitation } from './api/admin'
import { resendVerification, verifyMfa } from './api/auth'
import { isInternalRole } from './internalAccess'
import { getUiError } from './uiError'
import FormeLayout from './FormeLayout'

function AuthCard({ kicker, title, children }: { kicker: string; title: string; children: React.ReactNode }) {
  return <FormeLayout><section className="auth-card auth-card-wide auth-result-card">
    <p className="auth-kicker">{kicker}</p><h1>{title}</h1>{children}
  </section></FormeLayout>
}

export function RegistrationSentPage() {
  const [params] = useSearchParams()
  const email = params.get('email') ?? ''
  const deliveryFailed = params.get('delivery') === 'failed'
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  async function resend() {
    setBusy(true); setError(''); setNotice('')
    try { await resendVerification(email); setNotice('Nếu tài khoản đang chờ xác minh, email sẽ được gửi theo giới hạn an toàn.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  return <AuthCard kicker="BƯỚC CUỐI CÙNG" title="Kiểm tra email">
    <p className="auth-lead">{deliveryFailed ? 'Tài khoản đã được tạo nhưng chưa gửi được email. Hãy thử gửi lại bên dưới.' :
      <>Tài khoản đã được tạo. Mở liên kết xác minh gửi tới <strong>{email}</strong> trước khi đăng nhập.</>}</p>
    <div className="auth-step-list"><span>01 · Kiểm tra Hộp thư đến và Thư rác</span>
      <span>02 · Mở liên kết trong vòng 24 giờ</span><span>03 · Quay lại đăng nhập</span></div>
    {notice && <p className="auth-notice" role="status">{notice}</p>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    {email && <button className="auth-submit" disabled={busy} onClick={() => void resend()}>
      {busy ? 'ĐANG GỬI…' : 'GỬI LẠI EMAIL XÁC MINH'}</button>}
    <p className="auth-help"><Link to="/auth">← Quay lại đăng nhập</Link></p>
  </AuthCard>
}

export function ResendVerificationPage() {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try { await resendVerification(email); setDone(true) }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  return <AuthCard kicker="XÁC MINH TÀI KHOẢN" title="Gửi lại email">
    <p className="auth-lead">Nhập email đã đăng ký. Nếu tài khoản đang chờ xác minh, chúng tôi sẽ gửi liên kết mới.</p>
    {done ? <p className="auth-notice" role="status">Yêu cầu đã được tiếp nhận. Hãy kiểm tra hộp thư và thư rác.</p> :
      <form onSubmit={submit}><label>ĐỊA CHỈ EMAIL<input type="email" autoComplete="email" required value={email}
        onChange={(event) => setEmail(event.target.value)} /></label>
        <button className="auth-submit" disabled={busy}>GỬI LIÊN KẾT</button></form>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    <p className="auth-help"><Link to="/auth">← Quay lại đăng nhập</Link></p>
  </AuthCard>
}

export function MfaLoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { challengeToken?: string; email?: string;
    mfaMethod?: 'EMAIL' | 'TOTP' } | null
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!state?.challengeToken) return
    setBusy(true); setError('')
    try { const session = await verifyMfa(state.challengeToken, code)
      if (session.user) navigate(isInternalRole(session.user.role) ? '/internal' : '/', { replace: true }) }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  return <AuthCard kicker="BẢO MẬT HAI BƯỚC" title="Xác minh đăng nhập">
    <p className="auth-lead">{state?.mfaMethod === 'TOTP'
      ? 'Nhập mã sáu chữ số từ ứng dụng xác thực, hoặc dùng một mã khôi phục đã lưu.'
      : `Nhập mã sáu chữ số gửi tới ${state?.email ?? 'email của bạn'}, hoặc dùng một mã khôi phục đã lưu.`}</p>
    {!state?.challengeToken ? <p className="auth-error">Phiên xác minh đã mất. Hãy đăng nhập lại.</p> :
      <form onSubmit={submit}><label>MÃ XÁC MINH HOẶC MÃ KHÔI PHỤC<input maxLength={35}
        autoComplete="one-time-code" required value={code} onChange={(event) => setCode(event.target.value)} /></label>
        <button className="auth-submit" disabled={busy}>XÁC MINH</button></form>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    <p className="auth-help"><Link to="/auth">← Đăng nhập lại</Link></p>
  </AuthCard>
}

export function AcceptInvitationPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    if (!token) return
    if (password !== confirmPassword) { setError('Mật khẩu nhập lại không khớp.'); return }
    setBusy(true)
    try { await acceptInvitation({ token, password, confirmPassword }); setDone(true)
      window.history.replaceState(window.history.state, '', '/accept-invitation') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  return <AuthCard kicker="LỜI MỜI NHÂN VIÊN" title="Tham gia FORME">
    {done ? <><p className="auth-notice" role="status">Tài khoản đã được kích hoạt. Bạn có thể đăng nhập.</p>
      <Link className="auth-cta-link" to="/auth">ĐẾN TRANG ĐĂNG NHẬP →</Link></> :
      <><p className="auth-lead">Tự đặt mật khẩu để hoàn tất lời mời. Liên kết chỉ có hiệu lực một lần.</p>
        {!token ? <p className="auth-error">Liên kết lời mời không có mã hợp lệ.</p> :
          <form onSubmit={submit}><label>MẬT KHẨU<input type="password" minLength={8} maxLength={72}
            autoComplete="new-password" required value={password}
            onChange={(event) => setPassword(event.target.value)} /></label>
            <label>NHẬP LẠI MẬT KHẨU<input type="password" minLength={8} maxLength={72}
              autoComplete="new-password" required value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)} /></label>
            <button className="auth-submit" disabled={busy}>KÍCH HOẠT TÀI KHOẢN</button></form>}</>}
    {error && <p className="auth-error" role="alert">{error}</p>}
  </AuthCard>
}
