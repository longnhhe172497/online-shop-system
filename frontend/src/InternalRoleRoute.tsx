import { useEffect, useState, type ReactNode } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { getProfile } from './api/profile'
import { getInternalHome, isInternalRole } from './internalAccess'
import { getUiError } from './uiError'

export default function InternalRoleRoute({ role, children }: { role: string; children: ReactNode }) {
  const [currentRole, setCurrentRole] = useState<string | null>(null)
  const [checked, setChecked] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    getProfile().then((profile) => { if (active) setCurrentRole(profile.role) })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
      .finally(() => { if (active) setChecked(true) })
    return () => { active = false }
  }, [])

  if (!checked) return <main className="internal-access"><p>Đang kiểm tra quyền truy cập…</p></main>
  if (error) return <main className="internal-access"><section className="admin-panel">
    <h2>Không thể kiểm tra quyền truy cập</h2><p className="auth-error" role="alert">{error}</p>
    <Link to="/auth">Đăng nhập lại</Link></section></main>
  if (currentRole === role) return children
  if (currentRole && isInternalRole(currentRole)) return <Navigate to={getInternalHome(currentRole)} replace />
  return <main className="internal-access"><section className="admin-panel"><h2>Không có quyền truy cập</h2>
    <p>Trang này chỉ dành cho nhân viên được phân công.</p><Link to="/">Về cửa hàng</Link></section></main>
}
