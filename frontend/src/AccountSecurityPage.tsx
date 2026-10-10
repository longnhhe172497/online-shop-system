import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { changePassword, confirmMfa, confirmTotp, disableMfa, getSecurity, regenerateRecoveryCodes,
  startMfa, startMfaStepUp, startTotp } from './api/profile'
import { getUiError } from './uiError'
import AccountLayout from './AccountLayout'

export default function AccountSecurityPage() {
  const [mfaEnabled, setMfaEnabled] = useState(false)
  const [mfaMethod, setMfaMethod] = useState<'EMAIL' | 'TOTP'>('EMAIL')
  const [recoveryCodesRemaining, setRecoveryCodesRemaining] = useState(0)
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([])
  const [regeneratePassword, setRegeneratePassword] = useState('')
  const [regenerateCode, setRegenerateCode] = useState('')
  const [disableCode, setDisableCode] = useState('')
  const [stepUp, setStepUp] = useState<{ action: 'disable' | 'regenerate'; challengeToken: string;
    method: 'EMAIL' | 'TOTP' } | null>(null)
  const [totpPassword, setTotpPassword] = useState('')
  const [totpSecret, setTotpSecret] = useState('')
  const [totpCode, setTotpCode] = useState('')
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
    getSecurity().then((value) => { if (active) { setMfaEnabled(value.mfaEnabled)
      setMfaMethod(value.mfaMethod)
      setRecoveryCodesRemaining(value.recoveryCodesRemaining) } })
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
    try { const result = await confirmMfa(challenge, code); setMfaEnabled(true); setMfaMethod('EMAIL')
      setChallenge(''); setCode('')
      setRecoveryCodes(result.recoveryCodes); setRecoveryCodesRemaining(result.recoveryCodes.length)
      setNotice('Đã bật xác minh hai bước. Hãy lưu các mã khôi phục trước khi rời trang.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function turnOffMfa(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (stepUp?.action !== 'disable') {
        const result = await startMfaStepUp(disablePassword)
        setStepUp({ action: 'disable', challengeToken: result.challengeToken, method: result.mfaMethod })
        setNotice(result.mfaMethod === 'EMAIL' ? 'Đã gửi mã xác minh tới email.' : 'Nhập mã trong ứng dụng xác thực.')
        return
      }
      await disableMfa(disablePassword, stepUp.challengeToken, disableCode)
      setMfaEnabled(false); setDisablePassword(''); setDisableCode(''); setStepUp(null)
      setRecoveryCodes([]); setRecoveryCodesRemaining(0)
      setNotice('Đã tắt xác minh hai bước.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function regenerate(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('')
    try {
      if (stepUp?.action !== 'regenerate') {
        const result = await startMfaStepUp(regeneratePassword)
        setStepUp({ action: 'regenerate', challengeToken: result.challengeToken, method: result.mfaMethod })
        setNotice(result.mfaMethod === 'EMAIL' ? 'Đã gửi mã xác minh tới email.' : 'Nhập mã trong ứng dụng xác thực.')
        return
      }
      const result = await regenerateRecoveryCodes(regeneratePassword, stepUp.challengeToken, regenerateCode)
      setRegeneratePassword(''); setRegenerateCode(''); setStepUp(null); setRecoveryCodes(result.recoveryCodes)
      setRecoveryCodesRemaining(result.recoveryCodes.length)
      setNotice('Đã thay toàn bộ mã khôi phục cũ. Hãy lưu các mã mới ngay bây giờ.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function beginTotp(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('')
    try { const result = await startTotp(totpPassword); setTotpSecret(result.secret)
      setNotice('Thêm khóa vào ứng dụng xác thực, sau đó nhập mã sáu chữ số để hoàn tất.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function finishTotp(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try { const result = await confirmTotp(totpCode); setMfaEnabled(true); setMfaMethod('TOTP')
      setTotpSecret(''); setTotpCode(''); setTotpPassword('')
      setRecoveryCodes(result.recoveryCodes); setRecoveryCodesRemaining(result.recoveryCodes.length)
      setNotice('Đã bật ứng dụng xác thực. Hãy lưu các mã khôi phục trước khi rời trang.') }
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
      <h2>Xác minh hai bước</h2><span className={mfaEnabled ? 'security-state on' : 'security-state'}>
        {mfaEnabled ? (mfaMethod === 'TOTP' ? 'Ứng dụng xác thực' : 'Mã qua email') : 'Chưa bật'}</span>
      <p>Chọn mã qua email hoặc ứng dụng xác thực. Bạn chỉ có thể bật một phương thức tại một thời điểm.</p>
      {recoveryCodes.length > 0 && <div className="mfa-recovery-panel" role="status">
        <strong>Mã khôi phục — chỉ hiển thị lần này</strong>
        <p>Lưu các mã này ở nơi an toàn. Mỗi mã chỉ dùng được một lần khi bạn không nhận được email.</p>
        <div className="mfa-recovery-list">{recoveryCodes.map((item) => <code key={item}>{item}</code>)}</div>
        <button className="account-secondary" type="button" onClick={() => setRecoveryCodes([])}>
          Tôi đã lưu các mã</button></div>}
      {!mfaEnabled && !challenge && <button className="account-primary" disabled={busy} onClick={() => void beginMfa()}>
        Bật xác minh hai bước</button>}
      {!mfaEnabled && challenge && <form className="account-form" onSubmit={finishMfa}>
        <label>MÃ SÁU CHỮ SỐ<input inputMode="numeric" pattern="[0-9]{6}" maxLength={6}
          autoComplete="one-time-code" required value={code} onChange={(event) => setCode(event.target.value)} /></label>
        <button className="account-primary" disabled={busy}>Xác nhận và bật</button></form>}
      {!mfaEnabled && !totpSecret && <form className="account-form" onSubmit={beginTotp}>
        <p>Ứng dụng xác thực (Google Authenticator, Microsoft Authenticator…)</p>
        <label>MẬT KHẨU ĐỂ BẬT ỨNG DỤNG<input type="password" required autoComplete="current-password"
          value={totpPassword} onChange={(event) => setTotpPassword(event.target.value)} /></label>
        <button className="account-secondary" disabled={busy}>Thiết lập ứng dụng xác thực</button></form>}
      {!mfaEnabled && totpSecret && <form className="account-form" onSubmit={finishTotp}>
        <p>Nhập thủ công khóa dưới đây vào ứng dụng xác thực. Khóa chỉ hiển thị trong lần thiết lập này.</p>
        <code className="totp-secret">{totpSecret}</code>
        <label>MÃ TỪ ỨNG DỤNG<input inputMode="numeric" pattern="[0-9]{6}" maxLength={6} required
          autoComplete="one-time-code" value={totpCode} onChange={(event) => setTotpCode(event.target.value)} /></label>
        <button className="account-primary" disabled={busy}>Xác nhận ứng dụng xác thực</button></form>}
      {mfaEnabled && <form className="account-form" onSubmit={turnOffMfa}>
        <label>MẬT KHẨU ĐỂ TẮT<input type="password" required autoComplete="current-password"
          value={disablePassword} onChange={(event) => { setDisablePassword(event.target.value); setStepUp(null) }} /></label>
        {stepUp?.action === 'disable' && <label>MÃ MFA HIỆN TẠI HOẶC MÃ KHÔI PHỤC<input required
          autoComplete="one-time-code" value={disableCode}
          onChange={(event) => setDisableCode(event.target.value)} /></label>}
        <button className="account-secondary" disabled={busy}>
          {stepUp?.action === 'disable' ? 'Xác nhận tắt MFA' : 'Tắt xác minh hai bước'}</button></form>}
      {mfaEnabled && <form className="account-form" onSubmit={regenerate}>
        <p>Còn {recoveryCodesRemaining} mã khôi phục chưa dùng. Tạo mã mới sẽ vô hiệu hóa toàn bộ mã cũ.</p>
        <label>MẬT KHẨU ĐỂ TẠO MÃ MỚI<input type="password" required autoComplete="current-password"
          value={regeneratePassword} onChange={(event) => { setRegeneratePassword(event.target.value); setStepUp(null) }} /></label>
        {stepUp?.action === 'regenerate' && <label>MÃ MFA HIỆN TẠI HOẶC MÃ KHÔI PHỤC<input required
          autoComplete="one-time-code" value={regenerateCode}
          onChange={(event) => setRegenerateCode(event.target.value)} /></label>}
        <button className="account-secondary" disabled={busy}>
          {stepUp?.action === 'regenerate' ? 'Xác nhận tạo mã mới' : 'Tạo lại mã khôi phục'}</button></form>}
    </section></div>
  </AccountLayout>
}
