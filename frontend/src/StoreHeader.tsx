import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMe, logout, type AuthUser } from './api/auth'
import { getCart } from './api/cart'
import { CART_EVENT } from './cartEvents'
import { getInternalHome, isInternalRole } from './internalAccess'

function Icon({ name }: { name: 'user' | 'bag' | 'menu' }) {
  return <svg className="fs-icon" viewBox="0 0 24 24" aria-hidden="true">
    {name === 'user' && <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></>}
    {name === 'bag' && <><path d="M6 8h12l1 13H5L6 8Z" /><path d="M9 9V6a3 3 0 0 1 6 0v3" /></>}
    {name === 'menu' && <path d="M4 7h16M4 12h16M4 17h16" />}
  </svg>
}

/** Store top bar and header from the mock-ups. Staff accounts have no cart, so the bag is hidden for them. */
export default function StoreHeader() {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [count, setCount] = useState(0)
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    let active = true
    getMe().then((me) => { if (active) setUser(me) }).catch(() => {})
    getCart().then((cart) => { if (active) setCount(cart.itemCount) }).catch(() => {})
    const onCartChange = (event: Event) => setCount((event as CustomEvent<number>).detail)
    window.addEventListener(CART_EVENT, onCartChange)
    return () => {
      active = false
      window.removeEventListener(CART_EVENT, onCartChange)
    }
  }, [])

  async function signOut() {
    await logout().catch(() => {})
    setUser(null)
    setCount(0)
    setMenuOpen(false)
  }

  const staff = user !== null && isInternalRole(user.role)
  const links = <>
    <Link to="/">Sản phẩm</Link>
    {user?.role === 'CUSTOMER' && <Link to="/me/orders">Đơn hàng</Link>}
    {staff && user && <Link to={getInternalHome(user.role)}>Khu vực nội bộ</Link>}
    {user && <Link to="/me">Hồ sơ của tôi</Link>}
    {user && <button type="button" onClick={signOut}>Đăng xuất</button>}
  </>

  return <>
    <div className="fs-topbar">Thanh toán bằng mã QR hoặc khi nhận hàng (COD)</div>
    <header className="fs-header">
      <nav className="fs-nav" aria-label="Điều hướng cửa hàng">
        <Link className="fs-brand" to="/">Forme</Link>
        <div className="fs-navlinks">{links}</div>
        <div className="fs-navicons">
          {user && <span className="fs-user" title={user.email}>{user.fullName}</span>}
          <Link className="fs-iconbtn" to={user ? '/me' : '/auth'} aria-label={user ? 'Tài khoản của tôi' : 'Đăng nhập'}>
            <Icon name="user" /></Link>
          {!staff && <Link className="fs-iconbtn" to="/cart" aria-label={`Giỏ hàng${count > 0 ? ` (${count})` : ''}`}>
            <Icon name="bag" />{count > 0 && <span className="fs-count" aria-hidden="true">{count > 99 ? '99+' : count}</span>}
          </Link>}
          <button type="button" className="fs-iconbtn fs-mobile-only" aria-label="Mở menu" aria-expanded={menuOpen}
            aria-controls="fs-menu" onClick={() => setMenuOpen(!menuOpen)}><Icon name="menu" /></button>
        </div>
      </nav>
      {menuOpen && <div className="fs-menu fs-mobile-only" id="fs-menu" onClick={() => setMenuOpen(false)}>{links}</div>}
    </header>
  </>
}
