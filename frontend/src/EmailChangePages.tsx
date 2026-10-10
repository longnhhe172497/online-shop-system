import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { confirmEmailChange, getProfile, requestEmailChange } from './api/profile'
import { getUiError } from './uiError'
import AccountLayout from './AccountLayout'
import FormeLayout from './FormeLayout'

export function EmailChangePage() {
  const [currentEmail, setCurrentEmail] = useState('')
  const [newEmail, setNewEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [sent, setSent] = useState(false)
  useEffect(() => { let active = true
    getProfile().then((profile) => { if (active) setCurrentEmail(profile.email) })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
    return () => { active = false }
  }, [])
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setBusy(true)
    try { await requestEmailChange(newEmail, password); setPassword(''); setSent(true) }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  return <AccountLayout title="Đổi email đăng nhập" intro="Xác minh địa chỉ mới trước khi sử dụng để đăng nhập.">
    <section className="account-card security-panel email-panel"><p className="card-eyebrow">ĐỊA CHỈ EMAIL</p>
      <h2>Xác nhận email mới</h2><p>Email hiện tại: <strong>{currentEmail || 'Đang tải…'}</strong></p>
      <p>Chúng tôi sẽ gửi liên kết xác nhận tới địa chỉ mới. Email hiện tại vẫn sử dụng được cho tới khi bạn xác nhận.</p>
      {sent ? <p className="auth-notice" role="status">Đã gửi liên kết tới {newEmail}. Hãy mở email trong vòng 30 phút.</p> :
        <form className="account-form" onSubmit={submit}>
          <label>EMAIL MỚI<input type="email" required autoComplete="email" value={newEmail}
            onChange={(event) => setNewEmail(event.target.value)} /></label>
          <label>MẬT KHẨU HIỆN TẠI<input type="password" required autoComplete="current-password"
            value={password} onChange={(event) => setPassword(event.target.value)} /></label>
          <button className="account-primary" disabled={busy}>Gửi liên kết xác nhận</button></form>}
      {error && <p className="auth-error" role="alert">{error}</p>}
    </section>
  </AccountLayout>
}

export function EmailChangeConfirmPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')
  async function confirm() {
    if (!token) return
    setBusy(true); setError('')
    try { await confirmEmailChange(token); setDone(true)
      window.history.replaceState(window.history.state, '', '/email-change/confirm') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  return <FormeLayout><section className="auth-card auth-card-wide auth-result-card">
    <p className="auth-kicker">BẢO MẬT TÀI KHOẢN</p><h1>Xác nhận email</h1>
    {done ? <><p className="auth-notice" role="status">Email đăng nhập đã được đổi. Mọi thiết bị đã đăng xuất.</p>
      <Link to="/auth">Đăng nhập bằng email mới →</Link></> : <>
        <p className="auth-lead">Xác nhận địa chỉ email mới để hoàn tất thay đổi.</p>
        {token ? <button className="auth-submit" disabled={busy} onClick={() => void confirm()}>
          {busy ? 'ĐANG XÁC NHẬN…' : 'XÁC NHẬN EMAIL MỚI'}</button> :
          <p className="auth-error">Liên kết không có mã xác nhận.</p>}</>}
    {error && <p className="auth-error" role="alert">{error}</p>}
  </section></FormeLayout>
}
