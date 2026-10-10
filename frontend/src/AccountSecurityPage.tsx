import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { changePassword, confirmMfa, disableMfa, getSecurity, startMfa } from './api/profile'
import { getUiError } from './uiError'
import AccountLayout from './AccountLayout'

export default function AccountSecurityPage() {
  const [mfaEnabled, setMfaEnabled] = useState(false)
  const [challenge, setChallenge] = useState('')
  const [code, setCode] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [disablePassword, setDisablePassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const navigate = useNavigate()

  useEffect(() => { let active = true
    getSecurity().then((value) => { if (active) setMfaEnabled(value.mfaEnabled) })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  async function submitPassword(event: FormEvent) {
    event.preventDefault(); setError(''); setNotice('')
    if (newPassword !== confirmPassword) { setError('Mật khẩu nhập lại không khớp.'); return }
    setBusy(true)
    try { await changePassword({ currentPassword, newPassword, confirmPassword })
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('')
      navigate('/auth', { replace: true, state: { notice: 'Mật khẩu đã đổi. Hãy đăng nhập lại.' } }) }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function beginMfa() {
    setBusy(true); setError(''); setNotice('')
    try { const result = await startMfa(); setChallenge(result.challengeToken)
      setNotice('Đã gửi mã xác minh tới email của bạn. Mã có hiệu lực trong 10 phút.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function finishMfa(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try { await confirmMfa(challenge, code); setMfaEnabled(true); setChallenge(''); setCode('')
      setNotice('Đã bật xác minh hai bước. Lần đăng nhập tiếp theo sẽ cần mã email.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function turnOffMfa(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try { await disableMfa(disablePassword); setMfaEnabled(false); setDisablePassword('')
      setNotice('Đã tắt xác minh hai bước.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  return <AccountLayout title="Bảo mật tài khoản" intro="Đổi mật khẩu và bảo vệ lần đăng nhập tiếp theo.">
    {loading && <p>Đang tải thiết lập bảo mật…</p>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}
    <div className="security-grid"><section className="account-card security-panel"><p className="card-eyebrow">MẬT KHẨU</p>
      <h2>Đổi mật khẩu</h2><p>Tất cả phiên đăng nhập sẽ hết hiệu lực sau khi đổi.</p>
      <form className="account-form" onSubmit={submitPassword}>
        <label>MẬT KHẨU HIỆN TẠI<input type="password" required autoComplete="current-password"
          value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} /></label>
        <label>MẬT KHẨU MỚI<input type="password" required minLength={8} maxLength={72} autoComplete="new-password"
          value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label>
        <label>NHẬP LẠI MẬT KHẨU<input type="password" required minLength={8} maxLength={72} autoComplete="new-password"
          value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} /></label>
        <button className="account-primary" disabled={busy}>Cập nhật mật khẩu</button>
      </form><Link to="/forgot-password">Bạn không nhớ mật khẩu hiện tại?</Link>
    </section><section className="account-card security-panel"><p className="card-eyebrow">XÁC MINH HAI BƯỚC</p>
      <h2>Mã xác minh qua email</h2><span className={mfaEnabled ? 'security-state on' : 'security-state'}>
        {mfaEnabled ? 'Đang bật' : 'Chưa bật'}</span>
      <p>Khi bật, ngoài mật khẩu bạn cần nhập mã dùng một lần được gửi tới email.</p>
      {!mfaEnabled && !challenge && <button className="account-primary" disabled={busy} onClick={() => void beginMfa()}>
        Bật xác minh hai bước</button>}
      {!mfaEnabled && challenge && <form className="account-form" onSubmit={finishMfa}>
        <label>MÃ SÁU CHỮ SỐ<input inputMode="numeric" pattern="[0-9]{6}" maxLength={6}
          autoComplete="one-time-code" required value={code} onChange={(event) => setCode(event.target.value)} /></label>
        <button className="account-primary" disabled={busy}>Xác nhận và bật</button></form>}
      {mfaEnabled && <form className="account-form" onSubmit={turnOffMfa}>
        <label>MẬT KHẨU ĐỂ TẮT<input type="password" required autoComplete="current-password"
          value={disablePassword} onChange={(event) => setDisablePassword(event.target.value)} /></label>
        <button className="account-secondary" disabled={busy}>Tắt xác minh hai bước</button></form>}
    </section></div>
  </AccountLayout>
}
