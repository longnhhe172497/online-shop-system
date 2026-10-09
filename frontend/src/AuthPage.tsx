import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login, registerAccount } from './api/auth'
import { getUiError } from './uiError'

export default function AuthPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')
    if (mode === 'register' && password !== confirmPassword) {
      setError('Mật khẩu nhập lại không khớp.')
      return
    }
    setBusy(true)
    try {
      if (mode === 'register') {
        await registerAccount({ fullName, email, password, confirmPassword })
        setNotice('Tài khoản đã được tạo. Hãy kiểm tra email và mở liên kết xác minh trước khi đăng nhập.')
        setMode('login')
        setPassword('')
        setConfirmPassword('')
      } else {
        await login({ email, password })
        navigate('/')
      }
    } catch (failure) {
      setError(getUiError(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="forme-page">
      <div className="announcement">CỬA HÀNG TRỰC TUYẾN · BẢN CHẠY TRÊN MÁY CÁ NHÂN</div>
      <header className="forme-header">
        <Link to="/" className="brand">FORME</Link>
        <nav aria-label="Điều hướng chính"><Link to="/">CỬA HÀNG</Link><Link to="/auth">ĐĂNG NHẬP</Link></nav>
      </header>
      <main className="auth-main">
        <section className="auth-card">
          <p className="auth-kicker">{mode === 'login' ? 'CHÀO MỪNG BẠN' : 'THAM GIA CÙNG CHÚNG TÔI'}</p>
          <h1>{mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}</h1>
          <div className="auth-tabs" role="tablist" aria-label="Thao tác tài khoản">
            <button role="tab" aria-selected={mode === 'login'} className={mode === 'login' ? 'active' : ''}
              onClick={() => { setMode('login'); setError(''); setNotice('') }}>ĐĂNG NHẬP</button>
            <button role="tab" aria-selected={mode === 'register'} className={mode === 'register' ? 'active' : ''}
              onClick={() => { setMode('register'); setError(''); setNotice('') }}>ĐĂNG KÝ</button>
          </div>
          {notice && <p className="auth-notice" role="status">{notice}</p>}
          {error && <p className="auth-error" role="alert">{error}</p>}
          <form onSubmit={submit}>
            {mode === 'register' && <label>HỌ VÀ TÊN<input value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={150} autoComplete="name" /></label>}
            <label>ĐỊA CHỈ EMAIL<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" /></label>
            <label>MẬT KHẨU<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={mode === 'register' ? 8 : undefined} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>
            {mode === 'register' && <label>NHẬP LẠI MẬT KHẨU<input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required minLength={8} autoComplete="new-password" /></label>}
            <button className="auth-submit" disabled={busy} type="submit">{busy ? 'VUI LÒNG ĐỢI…' : mode === 'login' ? 'ĐĂNG NHẬP' : 'TẠO TÀI KHOẢN'}</button>
          </form>
          {mode === 'login' && <p className="auth-help"><Link to="/forgot-password">Quên mật khẩu?</Link></p>}
        </section>
      </main>
      <footer className="forme-footer"><strong>FORME</strong><span>Cửa hàng trực tuyến · Bản chạy thử trên máy cá nhân</span></footer>
    </div>
  )
}
