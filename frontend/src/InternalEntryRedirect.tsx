import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { getProfile } from './api/profile'
import { getInternalHome, isInternalRole } from './internalAccess'
import { getUiError } from './uiError'

export default function InternalEntryRedirect() {
  const location = useLocation()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [denied, setDenied] = useState(false)

  useEffect(() => {
    let active = true
    getProfile().then((profile) => {
      if (!active) return
      if (!isInternalRole(profile.role)) {
        setDenied(true)
        return
      }
      const home = getInternalHome(profile.role)
      const suffix = location.pathname.replace(/^\/internal/, '')
      const canKeepSuffix = profile.role === 'ADMIN' || profile.role === 'MANAGER'
      navigate(`${home}${canKeepSuffix ? suffix : ''}`, { replace: true })
    }).catch((failure) => { if (active) setError(getUiError(failure)) })
    return () => { active = false }
  }, [location.pathname, navigate])

  return <main className="internal-access"><section className="admin-panel">
    {denied ? <><h2>Không có quyền truy cập</h2><p>Chỉ nhân viên nội bộ được xem trang này.</p>
      <Link to="/">Về cửa hàng</Link></> : error ? <><h2>Không thể mở khu vực nội bộ</h2><p className="auth-error" role="alert">{error}</p>
      <Link to="/auth">Đăng nhập lại</Link></> : <p>Đang chuyển đến không gian làm việc của bạn…</p>}
  </section></main>
}
