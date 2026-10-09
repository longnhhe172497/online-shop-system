import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { verifyAccount } from './api/auth'

export default function VerifyPage() {
  const [params] = useSearchParams()
  const [status, setStatus] = useState('')
  const [busy, setBusy] = useState(false)
  const token = params.get('token')

  async function verify() {
    if (!token) return
    setBusy(true)
    try {
      await verifyAccount(token)
      setStatus('Email đã được xác minh. Bạn có thể đăng nhập.')
    } catch {
      setStatus('Liên kết xác minh không hợp lệ, đã hết hạn hoặc đã được sử dụng.')
    } finally {
      setBusy(false)
    }
  }

  return <main className="verify-page"><div className="verify-card">
    <p className="auth-kicker">XÁC MINH TÀI KHOẢN</p><h1>Xác minh email</h1>
    <p>{token ? 'Xác nhận email để kích hoạt tài khoản của bạn.' : 'Không tìm thấy mã xác minh trong liên kết.'}</p>
    {token && !status && <button className="auth-submit" disabled={busy} onClick={verify}>{busy ? 'ĐANG XÁC MINH…' : 'XÁC MINH EMAIL'}</button>}
    {status && <p role="status">{status}</p>}
    <Link to="/auth">Quay lại đăng nhập</Link>
  </div></main>
}
