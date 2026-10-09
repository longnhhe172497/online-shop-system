import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { confirmPasswordReset, requestPasswordReset } from './api/auth'
import { getUiError } from './uiError'

export default function PasswordResetPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [email, setEmail] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (token && newPassword !== confirmPassword) {
      setError('Mật khẩu nhập lại không khớp.')
      return
    }
    setBusy(true)
    try {
      if (token) {
        await confirmPasswordReset({ token, newPassword, confirmPassword })
      } else {
        await requestPasswordReset(email)
      }
      setSuccess(true)
      setNewPassword('')
      setConfirmPassword('')
    } catch (failure) {
      setError(getUiError(failure))
    } finally {
      setBusy(false)
    }
  }

  return <div className="forme-page">
    <div className="announcement">CỬA HÀNG TRỰC TUYẾN · BẢN CHẠY TRÊN MÁY CÁ NHÂN</div>
    <header className="forme-header"><Link to="/" className="brand">FORME</Link>
      <nav aria-label="Điều hướng chính"><Link to="/">CỬA HÀNG</Link><Link to="/auth">ĐĂNG NHẬP</Link></nav>
    </header>
    <main className="auth-main"><section className="auth-card">
      <p className="auth-kicker">KHÔI PHỤC TÀI KHOẢN</p>
      <h1>{token ? 'Đặt lại mật khẩu' : 'Quên mật khẩu'}</h1>
      {success ? <>
        <p className="auth-notice" role="status">{token
          ? 'Mật khẩu đã được cập nhật. Hãy đăng nhập bằng mật khẩu mới.'
          : 'Nếu email thuộc một tài khoản đang hoạt động, chúng tôi đã gửi liên kết đặt lại mật khẩu. Hãy kiểm tra hộp thư.'}</p>
        <p className="auth-help"><Link to="/auth">Quay lại đăng nhập</Link></p>
      </> : <>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <form onSubmit={submit}>
          {token ? <>
            <label>MẬT KHẨU MỚI<input type="password" value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)} required minLength={8}
              maxLength={72} autoComplete="new-password" /></label>
            <label>NHẬP LẠI MẬT KHẨU MỚI<input type="password" value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)} required minLength={8}
              maxLength={72} autoComplete="new-password" /></label>
          </> : <label>ĐỊA CHỈ EMAIL<input type="email" value={email}
            onChange={(event) => setEmail(event.target.value)} required autoComplete="email" /></label>}
          <button className="auth-submit" type="submit" disabled={busy}>{busy ? 'VUI LÒNG ĐỢI…'
            : token ? 'ĐẶT LẠI MẬT KHẨU' : 'GỬI LIÊN KẾT'}</button>
        </form>
        <p className="auth-help"><Link to="/auth">Quay lại đăng nhập</Link></p>
      </>}
    </section></main>
    <footer className="forme-footer"><strong>FORME</strong><span>Cửa hàng trực tuyến · Bản chạy thử trên máy cá nhân</span></footer>
  </div>
}
