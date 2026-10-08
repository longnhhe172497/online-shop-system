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
      setStatus('Email verified. You can now sign in.')
    } catch {
      setStatus('This verification link is invalid, expired, or already used.')
    } finally {
      setBusy(false)
    }
  }

  return <main className="verify-page"><div className="verify-card">
    <p className="auth-kicker">ONLINE SHOP SYSTEM</p><h1>Verify email</h1>
    <p>{token ? 'Confirm your email address to activate your account.' : 'No verification token was provided.'}</p>
    {token && !status && <button className="auth-submit" disabled={busy} onClick={verify}>{busy ? 'VERIFYING…' : 'VERIFY EMAIL'}</button>}
    {status && <p role="status">{status}</p>}
    <Link to="/auth">Back to sign in</Link>
  </div></main>
}
