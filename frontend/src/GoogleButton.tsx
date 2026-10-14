import { useEffect, useRef, useState } from 'react'
import { getGoogleConfig } from './api/auth'

type GoogleIdentity = {
  accounts: { id: {
    initialize: (options: { client_id: string; callback: (response: { credential: string }) => void;
      auto_select: boolean }) => void
    renderButton: (element: HTMLElement, options: Record<string, string | number>) => void
  } }
}

declare global { interface Window { google?: GoogleIdentity } }

let scriptPromise: Promise<void> | null = null
let initializedIdentity: GoogleIdentity['accounts']['id'] | null = null
let initializedClientId = ''
let activeCredentialHandler: ((credential: string) => void) | null = null
function loadGoogleScript(): Promise<void> {
  if (window.google) return Promise.resolve()
  if (!scriptPromise) scriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => { scriptPromise = null; reject(new Error('Không tải được Google Identity Services.')) }
    document.head.appendChild(script)
  })
  return scriptPromise
}

export default function GoogleButton({ onCredential, disabled = false }: {
  onCredential: (credential: string) => void; disabled?: boolean
}) {
  const host = useRef<HTMLDivElement>(null)
  const callback = useRef(onCredential)
  const [clientId, setClientId] = useState('')
  const [available, setAvailable] = useState<boolean | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { callback.current = onCredential }, [onCredential])

  useEffect(() => { let alive = true
    getGoogleConfig().then((config) => { if (alive) { setAvailable(config.enabled)
      if (config.enabled) setClientId(config.clientId) } })
      .catch(() => { if (alive) setError('Không đọc được cấu hình đăng nhập Google.') })
    return () => { alive = false }
  }, [])

  useEffect(() => {
    if (!clientId || disabled || !host.current) return
    let alive = true
    const handleCredential = (credential: string) => { if (alive) callback.current(credential) }
    loadGoogleScript().then(() => {
      if (!alive || !host.current || !window.google) return
      host.current.replaceChildren()
      const identity = window.google.accounts.id
      activeCredentialHandler = handleCredential
      if (initializedIdentity !== identity || initializedClientId !== clientId) {
        identity.initialize({ client_id: clientId,
          callback: (response) => activeCredentialHandler?.(response.credential), auto_select: false })
        initializedIdentity = identity
        initializedClientId = clientId
      }
      identity.renderButton(host.current, {
        type: 'standard', theme: 'outline', size: 'large', text: 'signin_with',
        shape: 'rectangular', logo_alignment: 'left', width: 400, locale: 'vi',
      })
    }).catch(() => { if (alive) setError('Không tải được nút đăng nhập Google.') })
    return () => { alive = false
      if (activeCredentialHandler === handleCredential) activeCredentialHandler = null }
  }, [clientId, disabled])

  if (error) return <p className="google-login-note" role="status">{error}</p>
  if (!clientId || disabled) return <><button type="button" className="google-button-placeholder" disabled>
    Đăng nhập bằng Google</button>
    {available === false && <p className="google-login-note">Tính năng Google chưa được cấu hình trên máy này.</p>}
  </>
  return <div className="google-button-host" ref={host} aria-label="Đăng nhập bằng Google" />
}
