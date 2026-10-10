import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { confirmPasswordReset, requestPasswordReset } from './api/auth'
import { getUiError } from './uiError'
import FormeLayout from './FormeLayout'

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
        window.history.replaceState(window.history.state, '', '/reset-password')
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

  return <FormeLayout><section className="auth-card auth-card-wide auth-result-card">
      <p className="auth-kicker">KHÔI PHỤC TÀI KHOẢN</p>
      <h1>{token ? 'Đặt lại mật khẩu' : 'Quên mật khẩu'}</h1>
      <p className="auth-lead">{token ? 'Chọn mật khẩu mới để tiếp tục sử dụng tài khoản.' :
        'Chúng tôi sẽ gửi một liên kết bảo mật tới email của bạn.'}</p>
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
    </section></FormeLayout>
}
