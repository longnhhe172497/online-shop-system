import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  changePassword, confirmMfa, confirmTotp, disableMfa, getSecurity, regenerateRecoveryCodes,
  startMfa, startMfaStepUp, startTotp
} from './api/profile'
import { getUiError } from './uiError'
import AccountLayout from './AccountLayout'
import GoogleButton from './GoogleButton'
import { getGoogleLinkStatus, linkGoogle, unlinkGoogle } from './api/auth'

export default function AccountSecurityPage() {
  const [mfaEnabled, setMfaEnabled] = useState(false)
  const [mfaMethod, setMfaMethod] = useState<'EMAIL' | 'TOTP'>('EMAIL')
  const [recoveryCodesRemaining, setRecoveryCodesRemaining] = useState(0)
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([])
  const [regeneratePassword, setRegeneratePassword] = useState('')
  const [regenerateCode, setRegenerateCode] = useState('')
  const [disableCode, setDisableCode] = useState('')
  const [stepUp, setStepUp] = useState<{
    action: 'disable' | 'regenerate'; challengeToken: string;
    method: 'EMAIL' | 'TOTP'
  } | null>(null)
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
  const [googleLinked, setGoogleLinked] = useState(false)
  const [googlePassword, setGooglePassword] = useState('')
  const [googleUnlinkPassword, setGoogleUnlinkPassword] = useState('')
  const [googleError, setGoogleError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const navigate = useNavigate()
  const pendingMfaMethod = !mfaEnabled && (totpSecret ? 'TOTP' : challenge ? 'EMAIL' : null)
  const stepUpCodeLabel = stepUp?.method === 'TOTP'
    ? 'MÃ 6 SỐ TỪ ỨNG DỤNG HOẶC MÃ KHÔI PHỤC'
    : 'MÃ 6 SỐ QUA EMAIL HOẶC MÃ KHÔI PHỤC'

  useEffect(() => {
    let active = true
    getSecurity().then((value) => {
      if (active) {
        setMfaEnabled(value.mfaEnabled)
        setMfaMethod(value.mfaMethod)
        setRecoveryCodesRemaining(value.recoveryCodesRemaining)
      }
    })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    getGoogleLinkStatus().then((status) => { if (active) setGoogleLinked(status.linked) })
      .catch((failure) => { if (active) setGoogleError(getUiError(failure)) })
    return () => { active = false }
  }, [])

  async function connectGoogle(credential: string) {
    setBusy(true); setGoogleError(''); setNotice('')
    try {
      await linkGoogle(googlePassword, credential); setGoogleLinked(true); setGooglePassword('')
      setNotice('Đã liên kết Google. Bạn có thể dùng nút Google ở màn hình đăng nhập.')
    }
    catch (failure) { setGoogleError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function disconnectGoogle(event: FormEvent) {
    event.preventDefault(); setGoogleError(''); setNotice(''); setBusy(true)
    try {
      await unlinkGoogle(googleUnlinkPassword)
      setGoogleLinked(false); setGoogleUnlinkPassword('')
      setNotice('Đã hủy liên kết Google. Bạn vẫn đăng nhập được bằng email và mật khẩu FORME.')
    } catch (failure) { setGoogleError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function submitPassword(event: FormEvent) {
    event.preventDefault(); setError(''); setNotice('')
    if (newPassword !== confirmPassword) { setError('Mật khẩu nhập lại không khớp.'); return }
    setBusy(true)
    try {
      await changePassword({ currentPassword, newPassword, confirmPassword })
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('')
      navigate('/auth', { replace: true, state: { notice: 'Mật khẩu đã đổi. Hãy đăng nhập lại.' } })
    }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function beginMfa() {
    setBusy(true); setError(''); setNotice('')
    try {
      const result = await startMfa(); setChallenge(result.challengeToken)
      setNotice('Đã gửi mã 6 số tới email của bạn. Nhập mã vào ô bên dưới trong 10 phút để hoàn tất bật xác minh hai bước.')
    }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function finishMfa(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const result = await confirmMfa(challenge, code); setMfaEnabled(true); setMfaMethod('EMAIL')
      setChallenge(''); setCode('')
      setRecoveryCodes(result.recoveryCodes); setRecoveryCodesRemaining(result.recoveryCodes.length)
      setNotice('Đã bật mã qua email. Từ lần đăng nhập sau, bạn sẽ nhập mã 6 số được gửi tới email. Hãy lưu 8 mã khôi phục bên dưới để dùng khi không nhận được email.')
    }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function turnOffMfa(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (stepUp?.action !== 'disable') {
        const result = await startMfaStepUp(disablePassword)
        setStepUp({ action: 'disable', challengeToken: result.challengeToken, method: result.mfaMethod })
        setNotice(result.emailDeliveryFailed ? 'Không gửi được mã qua email. Hãy dùng một mã khôi phục đã lưu để xác nhận thao tác này.' : result.mfaMethod === 'EMAIL' ? 'Đã gửi mã 6 số tới email. Nhập mã bên dưới, hoặc dùng một mã khôi phục nếu không nhận được email.' :
          'Mở ứng dụng xác thực và nhập mã 6 số của FORME bên dưới, hoặc dùng một mã khôi phục nếu không mở được ứng dụng.')
        return
      }
      await disableMfa(disablePassword, stepUp.challengeToken, disableCode)
      setMfaEnabled(false); setDisablePassword(''); setDisableCode(''); setStepUp(null)
      setRecoveryCodes([]); setRecoveryCodesRemaining(0)
      setNotice('Đã tắt xác minh hai bước. Những lần đăng nhập tiếp theo sẽ không yêu cầu mã xác minh.')
    }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function regenerate(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('')
    try {
      if (stepUp?.action !== 'regenerate') {
        const result = await startMfaStepUp(regeneratePassword)
        setStepUp({ action: 'regenerate', challengeToken: result.challengeToken, method: result.mfaMethod })
        setNotice(result.emailDeliveryFailed ? 'Không gửi được mã qua email. Hãy dùng một mã khôi phục đã lưu để xác nhận thao tác này.' : result.mfaMethod === 'EMAIL' ? 'Đã gửi mã 6 số tới email. Nhập mã bên dưới, hoặc dùng một mã khôi phục nếu không nhận được email.' :
          'Mở ứng dụng xác thực và nhập mã 6 số của FORME bên dưới, hoặc dùng một mã khôi phục nếu không mở được ứng dụng.')
        return
      }
      const result = await regenerateRecoveryCodes(regeneratePassword, stepUp.challengeToken, regenerateCode)
      setRegeneratePassword(''); setRegenerateCode(''); setStepUp(null); setRecoveryCodes(result.recoveryCodes)
      setRecoveryCodesRemaining(result.recoveryCodes.length)
      setNotice('Đã tạo 8 mã khôi phục mới. Toàn bộ mã cũ không còn dùng được; hãy lưu mã mới ngay bây giờ.')
    }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function beginTotp(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('')
    try {
      const result = await startTotp(totpPassword); setTotpSecret(result.secret)
      setNotice('Đây là khóa thiết lập, không phải mã đăng nhập. Thêm khóa vào ứng dụng xác thực, rồi nhập mã 6 số do ứng dụng tạo ra để hoàn tất.')
    }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function finishTotp(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const result = await confirmTotp(totpCode); setMfaEnabled(true); setMfaMethod('TOTP')
      setTotpSecret(''); setTotpCode(''); setTotpPassword('')
      setRecoveryCodes(result.recoveryCodes); setRecoveryCodesRemaining(result.recoveryCodes.length)
      setNotice('Đã bật ứng dụng xác thực. Từ lần đăng nhập sau, hãy lấy mã 6 số mới trong ứng dụng. Lưu 8 mã khôi phục bên dưới để dùng khi không mở được ứng dụng.')
    }
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
          {mfaEnabled ? (mfaMethod === 'TOTP' ? 'Đã bật · Ứng dụng xác thực' : 'Đã bật · Mã qua email') :
            pendingMfaMethod ? (pendingMfaMethod === 'TOTP' ? 'Đang thiết lập ứng dụng' : 'Đang xác nhận email') :
              'Chưa bật'}</span>
        <p>Chọn mã qua email hoặc ứng dụng xác thực. Bạn chỉ có thể bật một phương thức tại một thời điểm.</p>
        {mfaEnabled && <p>{mfaMethod === 'TOTP'
          ? 'Khi đăng nhập, mở ứng dụng xác thực và nhập mã 6 số đang hiển thị cho FORME. Mã khôi phục chỉ dùng khi bạn không truy cập được ứng dụng.'
          : 'Khi đăng nhập, nhập mã 6 số được gửi tới email. Mã khôi phục chỉ dùng khi bạn không nhận được email.'}</p>}
        {pendingMfaMethod && <p role="status">Chưa bật xong. Hãy nhập mã xác nhận bên dưới để hoàn tất;
          phương thức còn lại sẽ chỉ chọn được sau khi thiết lập này kết thúc.</p>}
        {recoveryCodes.length > 0 && <div className="mfa-recovery-panel" role="status">
          <strong>Mã khôi phục — chỉ hiển thị lần này</strong>
          <p>Đây là mã dự phòng, không cần nhập vào ứng dụng xác thực. Lưu ở nơi an toàn;
            mỗi mã chỉ dùng được một lần khi bạn không lấy được mã xác minh thông thường.</p>
          <div className="mfa-recovery-list">{recoveryCodes.map((item) => <code key={item}>{item}</code>)}</div>
          <button className="account-secondary" type="button" onClick={() => setRecoveryCodes([])}>
            Tôi đã lưu các mã</button></div>}
        {!mfaEnabled && !challenge && !totpSecret && <button className="account-primary" disabled={busy || loading}
          onClick={() => void beginMfa()}>Bật mã qua email</button>}
        {!mfaEnabled && challenge && <form className="account-form" onSubmit={finishMfa}>
          <label>MÃ SÁU CHỮ SỐ<input inputMode="numeric" pattern="[0-9]{6}" maxLength={6}
            autoComplete="one-time-code" required value={code} onChange={(event) => setCode(event.target.value)} /></label>
          <button className="account-primary" disabled={busy}>Xác nhận và bật</button></form>}
        {!mfaEnabled && !challenge && !totpSecret && <form className="account-form" onSubmit={beginTotp}>
          <p>Ứng dụng xác thực (Google Authenticator, Microsoft Authenticator…)</p>
          <label>MẬT KHẨU ĐỂ BẬT ỨNG DỤNG<input type="password" required autoComplete="current-password"
            value={totpPassword} onChange={(event) => setTotpPassword(event.target.value)} /></label>
          <p>Nếu bạn chỉ từng đăng nhập bằng Google và chưa có mật khẩu FORME, hãy dùng “Quên mật khẩu” ở màn đăng nhập để đặt mật khẩu trước.</p>
          <button className="account-secondary" disabled={busy || loading}>Thiết lập ứng dụng xác thực</button></form>}
        {!mfaEnabled && totpSecret && <form className="account-form" onSubmit={finishTotp}>
          <p>Bước 1: Thêm khóa thiết lập dưới đây vào Google Authenticator hoặc ứng dụng xác thực bạn dùng.
            Khóa này chỉ hiển thị trong lần thiết lập, không phải mã để đăng nhập.</p>
          <code className="totp-secret">{totpSecret}</code>
          <p>Bước 2: Mở mục FORME vừa thêm trong ứng dụng và nhập mã 6 số ứng dụng đang hiển thị vào đây.</p>
          <label>MÃ 6 SỐ TỪ ỨNG DỤNG<input inputMode="numeric" pattern="[0-9]{6}" maxLength={6} required
            autoComplete="one-time-code" value={totpCode} onChange={(event) => setTotpCode(event.target.value)} /></label>
          <button className="account-primary" disabled={busy}>Xác nhận ứng dụng xác thực</button></form>}
        {mfaEnabled && <form className="account-form" onSubmit={turnOffMfa}>
          <label>MẬT KHẨU ĐỂ TẮT<input type="password" required autoComplete="current-password"
            value={disablePassword} onChange={(event) => { setDisablePassword(event.target.value); setStepUp(null) }} /></label>
          {stepUp?.action === 'disable' && <label>{stepUpCodeLabel}<input required
            autoComplete="one-time-code" value={disableCode}
            onChange={(event) => setDisableCode(event.target.value)} /></label>}
          <button className="account-secondary" disabled={busy}>
            {stepUp?.action === 'disable' ? 'Xác nhận tắt MFA' : 'Tắt xác minh hai bước'}</button></form>}
        {mfaEnabled && <form className="account-form" onSubmit={regenerate}>
          <p>Còn {recoveryCodesRemaining} mã khôi phục chưa dùng. Tạo mã mới sẽ vô hiệu hóa toàn bộ mã cũ.</p>
          <label>MẬT KHẨU ĐỂ TẠO MÃ MỚI<input type="password" required autoComplete="current-password"
            value={regeneratePassword} onChange={(event) => { setRegeneratePassword(event.target.value); setStepUp(null) }} /></label>
          {stepUp?.action === 'regenerate' && <label>{stepUpCodeLabel}<input required
            autoComplete="one-time-code" value={regenerateCode}
            onChange={(event) => setRegenerateCode(event.target.value)} /></label>}
          <button className="account-secondary" disabled={busy}>
            {stepUp?.action === 'regenerate' ? 'Xác nhận tạo mã mới' : 'Tạo lại mã khôi phục'}</button></form>}
      </section><section className="account-card security-panel"><p className="card-eyebrow">TÀI KHOẢN LIÊN KẾT</p>
        <h2>Đăng nhập bằng Google</h2>
        {googleLinked ? <>
          <p className="security-state on">Đã liên kết Google</p>
          <p>Hủy liên kết sẽ ngừng đăng nhập bằng Google. Bạn vẫn cần mật khẩu FORME để đăng nhập.</p>
          <form className="account-form" onSubmit={disconnectGoogle}>
            <label>MẬT KHẨU ĐỂ HỦY LIÊN KẾT<input type="password" required autoComplete="current-password"
              value={googleUnlinkPassword} onChange={(event) => { setGoogleUnlinkPassword(event.target.value); setGoogleError('') }} /></label>
            <button className="account-secondary" disabled={busy}>Hủy liên kết Google</button>
          </form>
          <p>Nếu bạn chỉ từng đăng nhập bằng Google, hãy đặt mật khẩu FORME qua “Quên mật khẩu” trước khi hủy liên kết.</p>
          {googleError && <p className="auth-error" role="alert">{googleError}</p>}
        </> : <>
          <p>Liên kết tài khoản Google có cùng email để đăng nhập nhanh hơn. Việc này không đổi quyền tài khoản.</p>
          <label className="google-link-password">MẬT KHẨU ĐỂ LIÊN KẾT GOOGLE<input type="password"
            autoComplete="current-password" value={googlePassword}
            onChange={(event) => { setGooglePassword(event.target.value); setGoogleError('') }} /></label>
          <GoogleButton disabled={busy || !googlePassword} onCredential={(credential) => void connectGoogle(credential)} />
          {googleError && <p className="auth-error" role="alert">{googleError}</p>}
        </>}
      </section></div>
  </AccountLayout>
}
