import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { confirmPasswordReset, requestPasswordReset } from './api/auth'

function getError(error: unknown) {
  if (isAxiosError(error)) return error.response?.data?.detail || 'The request could not be completed.'
  return 'The request could not be completed.'
}

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
      setError('Passwords do not match.')
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
      setError(getError(failure))
    } finally {
      setBusy(false)
    }
  }

  return <div className="forme-page">
    <div className="announcement">ONLINE SHOP SYSTEM · LOCAL DEVELOPMENT</div>
    <header className="forme-header"><Link to="/" className="brand">FORME</Link>
      <nav aria-label="Main navigation"><Link to="/">SHOP</Link><Link to="/auth">SIGN IN</Link></nav>
    </header>
    <main className="auth-main"><section className="auth-card">
      <p className="auth-kicker">ACCOUNT RECOVERY</p>
      <h1>{token ? 'Reset password' : 'Forgot password'}</h1>
      {success ? <>
        <p className="auth-notice" role="status">{token
          ? 'Password updated. Sign in with your new password.'
          : 'If an active account exists for this email, a reset link has been sent. Check your inbox.'}</p>
        <p className="auth-help"><Link to="/auth">Back to sign in</Link></p>
      </> : <>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <form onSubmit={submit}>
          {token ? <>
            <label>NEW PASSWORD<input type="password" value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)} required minLength={8}
              maxLength={72} autoComplete="new-password" /></label>
            <label>CONFIRM NEW PASSWORD<input type="password" value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)} required minLength={8}
              maxLength={72} autoComplete="new-password" /></label>
          </> : <label>EMAIL ADDRESS<input type="email" value={email}
            onChange={(event) => setEmail(event.target.value)} required autoComplete="email" /></label>}
          <button className="auth-submit" type="submit" disabled={busy}>{busy ? 'PLEASE WAIT…'
            : token ? 'SET NEW PASSWORD' : 'SEND RESET LINK'}</button>
        </form>
        <p className="auth-help"><Link to="/auth">Back to sign in</Link></p>
      </>}
    </section></main>
    <footer className="forme-footer"><strong>FORME</strong><span>Online Shop System · Local demo</span></footer>
  </div>
}
