import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { login, registerAccount } from './api/auth'
import { getUiError } from './uiError'
import { isInternalRole } from './internalAccess'
import FormeLayout from './FormeLayout'

export default function AuthPage() {
  const location = useLocation()
  const mode = location.pathname === '/register' ? 'register' : 'login'
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (mode === 'register' && password !== confirmPassword) {
      setError('Mật khẩu nhập lại không khớp.')
      return
    }
    setBusy(true)
    try {
      if (mode === 'register') {
        const result = await registerAccount({ fullName, email, password, confirmPassword })
        setPassword(''); setConfirmPassword('')
        navigate(`/registration-sent?email=${encodeURIComponent(email)}${result.emailSent ? '' : '&delivery=failed'}`)
      } else {
        const session = await login({ email, password })
        setPassword('')
        if (session.mfaRequired && session.challengeToken) {
          navigate('/auth/mfa', { state: { challengeToken: session.challengeToken, email,
            mfaMethod: session.mfaMethod } })
        } else if (session.user) {
          navigate(isInternalRole(session.user.role) ? '/internal' : '/')
        }
      }
    } catch (failure) {
      setError(getUiError(failure))
    } finally {
      setBusy(false)
    }
  }

  return <FormeLayout showcase><section className="auth-card auth-card-wide">
    <p className="auth-kicker">{mode === 'login' ? 'CHÀO MỪNG BẠN' : 'THAM GIA CÙNG CHÚNG TÔI'}</p>
    <h1>{mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}</h1>
    <p className="auth-lead">{mode === 'login' ? 'Tiếp tục hành trình mua sắm cùng FORME.' :
      'Tạo tài khoản để lưu địa chỉ và theo dõi đơn hàng.'}</p>
    <div className="auth-tabs" role="tablist" aria-label="Thao tác tài khoản">
      <button role="tab" aria-selected={mode === 'login'} className={mode === 'login' ? 'active' : ''}
        onClick={() => { setError(''); navigate('/auth') }}>ĐĂNG NHẬP</button>
      <button role="tab" aria-selected={mode === 'register'} className={mode === 'register' ? 'active' : ''}
        onClick={() => { setError(''); navigate('/register') }}>ĐĂNG KÝ</button>
    </div>
    {error && <p className="auth-error" role="alert">{error}</p>}
    {mode === 'login' && typeof location.state?.notice === 'string' &&
      <p className="auth-notice" role="status">{location.state.notice}</p>}
    <form onSubmit={submit}>
      {mode === 'register' && <label>HỌ VÀ TÊN<input value={fullName}
        onChange={(event) => setFullName(event.target.value)} required maxLength={150} autoComplete="name" /></label>}
      <label>ĐỊA CHỈ EMAIL<input type="email" value={email} onChange={(event) => setEmail(event.target.value)}
        required autoComplete="email" /></label>
      <label htmlFor="auth-password">MẬT KHẨU</label>
      <div className="password-input"><input id="auth-password" type={showPassword ? 'text' : 'password'}
        value={password} onChange={(event) => setPassword(event.target.value)} required
        minLength={mode === 'register' ? 8 : undefined}
        autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
        <button type="button" onClick={() => setShowPassword((value) => !value)}
          aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>{showPassword ? 'Ẩn' : 'Hiện'}</button></div>
      {mode === 'register' && <><p className="password-hint">Sử dụng ít nhất 8 ký tự. Nên chọn một mật khẩu riêng cho FORME.</p>
        <label>NHẬP LẠI MẬT KHẨU<input type={showPassword ? 'text' : 'password'} value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)} required minLength={8}
          autoComplete="new-password" /></label></>}
      <button className="auth-submit" disabled={busy} type="submit">{busy ? 'VUI LÒNG ĐỢI…' :
        mode === 'login' ? 'ĐĂNG NHẬP' : 'TẠO TÀI KHOẢN'}</button>
    </form>
    <div className="auth-links">{mode === 'login' ? <><Link to="/forgot-password">Quên mật khẩu?</Link>
      <Link to="/resend-verification">Gửi lại email xác minh</Link></> :
      <Link to="/auth">Đã có tài khoản? Đăng nhập</Link>}</div>
  </section></FormeLayout>
}
