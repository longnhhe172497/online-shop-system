import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMe, logout, type AuthUser } from './api/auth'
import { isInternalRole } from './internalAccess'

export default function StoreHeader() {
  const [user, setUser] = useState<AuthUser | null>(null)

  useEffect(() => {
    let active = true
    getMe().then((me) => { if (active) setUser(me) }).catch(() => {})
    return () => { active = false }
  }, [])

  async function signOut() {
    await logout().catch(() => {})
    setUser(null)
  }

  return <header className="forme-header shop-header">
    <Link className="brand" to="/">FORME</Link>
    <nav aria-label="Điều hướng cửa hàng"><Link to="/">SẢN PHẨM</Link></nav>
    <div className="shop-account">
      {user ? <>
        {isInternalRole(user.role) && <Link to="/internal">Quản lý nội bộ</Link>}
        <Link to="/me">Hồ sơ của tôi</Link>
        <button type="button" onClick={signOut}>Đăng xuất · {user.fullName}</button>
      </> : <Link to="/auth">Đăng nhập</Link>}
    </div>
  </header>
}