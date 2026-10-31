import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMe, logout, type AuthUser } from './api/auth'
import { getCart } from './api/cart'
import { CART_EVENT } from './cartEvents'
import { getInternalHome, isInternalRole } from './internalAccess'

export default function StoreHeader() {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [count, setCount] = useState(0)

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
  }

  const showCart = !user || !isInternalRole(user.role)

  return <header className="forme-header shop-header">
    <Link className="brand" to="/">FORME</Link>
    <nav aria-label="Điều hướng cửa hàng"><Link to="/">SẢN PHẨM</Link></nav>
    <div className="shop-account">
      {showCart && <Link className="shop-cart-link" to="/cart">Giỏ hàng{count > 0 ? ` (${count})` : ''}</Link>}
      {user ? <>
        {user.role === 'CUSTOMER' && <Link to="/me/orders">Đơn hàng</Link>}
        {isInternalRole(user.role) && <Link to={getInternalHome(user.role)}>Khu vực nội bộ</Link>}
        <Link to="/me">Hồ sơ của tôi</Link>
        <button type="button" onClick={signOut}>Đăng xuất · {user.fullName}</button>
      </> : <Link to="/auth">Đăng nhập</Link>}
    </div>
  </header>
}
