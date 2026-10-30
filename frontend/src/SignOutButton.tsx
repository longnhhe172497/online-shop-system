import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { logout } from './api/auth'
import { getUiError } from './uiError'

export default function SignOutButton({ className = '' }: { className?: string }) {
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function signOut() {
    setBusy(true)
    setError('')
    try {
      await logout()
      navigate('/auth', { replace: true, state: { notice: 'Đã đăng xuất.' } })
    } catch (failure) {
      setError(getUiError(failure))
    } finally {
      setBusy(false)
    }
  }

  return <><button type="button" className={className} disabled={busy} onClick={() => void signOut()}>
    {busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
    {error && <span className="auth-error" role="alert">{error}</span>}</>
}
