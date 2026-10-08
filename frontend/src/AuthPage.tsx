import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { login, registerAccount } from './api/auth'

function getError(error: unknown) {
  if (isAxiosError(error)) return error.response?.data?.detail || 'The request could not be completed.'
  return 'The request could not be completed.'
}

export default function AuthPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')
    setBusy(true)
    try {
      if (mode === 'register') {
        await registerAccount({ fullName, email, password })
        setNotice('Account created. Open MailHog at localhost:8025 and use the verification link before signing in.')
        setMode('login')
        setPassword('')
      } else {
        await login({ email, password })
        navigate('/')
      }
    } catch (failure) {
      setError(getError(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="forme-page">
      <div className="announcement">ONLINE SHOP SYSTEM · LOCAL DEVELOPMENT</div>
      <header className="forme-header">
        <Link to="/" className="brand">FORME</Link>
        <nav aria-label="Main navigation"><Link to="/">SHOP</Link><Link to="/auth">SIGN IN</Link></nav>
      </header>
      <main className="auth-main">
        <section className="auth-card">
          <p className="auth-kicker">{mode === 'login' ? 'WELCOME' : 'JOIN US'}</p>
          <h1>{mode === 'login' ? 'Sign in' : 'Create account'}</h1>
          <div className="auth-tabs" role="tablist" aria-label="Account action">
            <button role="tab" aria-selected={mode === 'login'} className={mode === 'login' ? 'active' : ''}
              onClick={() => { setMode('login'); setError(''); setNotice('') }}>SIGN IN</button>
            <button role="tab" aria-selected={mode === 'register'} className={mode === 'register' ? 'active' : ''}
              onClick={() => { setMode('register'); setError(''); setNotice('') }}>REGISTER</button>
          </div>
          {notice && <p className="auth-notice" role="status">{notice}</p>}
          {error && <p className="auth-error" role="alert">{error}</p>}
          <form onSubmit={submit}>
            {mode === 'register' && <label>FULL NAME<input value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={150} autoComplete="name" /></label>}
            <label>EMAIL ADDRESS<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" /></label>
            <label>PASSWORD<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={mode === 'register' ? 8 : undefined} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>
            <button className="auth-submit" disabled={busy} type="submit">{busy ? 'PLEASE WAIT…' : mode === 'login' ? 'SIGN IN' : 'CREATE ACCOUNT'}</button>
          </form>
          {mode === 'login' && <p className="auth-help">Forgot your password? Password reset is planned for the next auth PR.</p>}
        </section>
      </main>
      <footer className="forme-footer"><strong>FORME</strong><span>Online Shop System · Local demo</span></footer>
    </div>
  )
}
