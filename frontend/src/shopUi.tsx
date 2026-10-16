import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

export type IconName = 'cart' | 'bag' | 'truck' | 'shield' | 'qr' | 'cash' | 'ticket' | 'check' | 'pin' | 'clock'
  | 'return' | 'box' | 'lock' | 'x' | 'arrow'

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  const common = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8,
    strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const }
  return <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" {...common}>
    {name === 'cart' && <><circle cx="9" cy="20" r="1.4" /><circle cx="18" cy="20" r="1.4" />
      <path d="M2.5 3.5h2.6l2.3 11.2a1.6 1.6 0 0 0 1.6 1.3h8.6a1.6 1.6 0 0 0 1.6-1.2l1.6-6.8H6" /></>}
    {name === 'bag' && <><path d="M5 7h14l-1 13H6L5 7Z" /><path d="M9 7V5.5a3 3 0 0 1 6 0V7" /></>}
    {name === 'truck' && <><path d="M2.5 6h11v10h-11zM13.5 9.5h4l3 3.2V16h-7" /><circle cx="6.5" cy="17.5" r="1.8" />
      <circle cx="17" cy="17.5" r="1.8" /></>}
    {name === 'shield' && <><path d="M12 3 4.5 6v5.5c0 4.6 3.2 8.1 7.5 9.5 4.3-1.4 7.5-4.9 7.5-9.5V6L12 3Z" /><path d="m9 12 2 2 4-4" /></>}
    {name === 'qr' && <><rect x="3.5" y="3.5" width="6.5" height="6.5" rx="1" /><rect x="14" y="3.5" width="6.5" height="6.5" rx="1" />
      <rect x="3.5" y="14" width="6.5" height="6.5" rx="1" /><path d="M14 14h2.5v2.5H14zM18 18h2.5v2.5H18zM18 14h2.5M14 18v2.5" /></>}
    {name === 'cash' && <><rect x="2.5" y="6" width="19" height="12" rx="2" /><circle cx="12" cy="12" r="2.6" />
      <path d="M6 9.5v5M18 9.5v5" /></>}
    {name === 'ticket' && <><path d="M3 8a2 2 0 0 0 0 4v4h18v-4a2 2 0 0 1 0-4V4H3v4Z" /><path d="M14 4v12" strokeDasharray="2 2" /></>}
    {name === 'check' && <path d="m5 12.5 4.5 4.5L19 7.5" />}
    {name === 'pin' && <><path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z" /><circle cx="12" cy="10" r="2.3" /></>}
    {name === 'clock' && <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>}
    {name === 'return' && <><path d="M9 14 4.5 9.5 9 5" /><path d="M4.5 9.5H15a5 5 0 0 1 0 10h-3" /></>}
    {name === 'box' && <><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z" /><path d="m4 7.5 8 4.5 8-4.5M12 12v9" /></>}
    {name === 'lock' && <><rect x="5" y="10.5" width="14" height="10" rx="2" /><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" /></>}
    {name === 'x' && <path d="m6 6 12 12M18 6 6 18" />}
    {name === 'arrow' && <path d="M5 12h14M13 6l6 6-6 6" />}
  </svg>
}

const thumbColors = ['#e8ebff', '#fdecef', '#e6f6ef', '#fff4e0', '#eef0f3', '#f3e9ff', '#e5f4fb']
/** Products have no photos yet, so lines show a stable coloured tile with the product initials. */
export function ProductThumb({ name, sku, size = 64 }: { name: string; sku: string; size?: number }) {
  const hash = [...sku].reduce((value, char) => (value * 31 + char.charCodeAt(0)) >>> 0, 3)
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((word) => word[0]).join('').toUpperCase()
  return <span className="product-thumb" style={{ background: thumbColors[hash % thumbColors.length], width: size, height: size }}
    aria-hidden="true">{initials}</span>
}

export function ShopLayout({ children, minimal = false }: { children: ReactNode; minimal?: boolean }) {
  return <div className="forme-page shop-page">
    <div className="announcement">ĐỔI TRẢ TRONG 7 NGÀY · GIAO HÀNG TOÀN QUỐC</div>
    <header className="shop-header"><div className="shop-header-inner">
      <Link to="/" className="brand">FORME</Link>
      {minimal ? <span className="secure-note"><Icon name="lock" size={16} /> Thanh toán an toàn</span>
        : <nav aria-label="Điều hướng cửa hàng"><Link to="/">Cửa hàng</Link><Link to="/me/orders">Đơn hàng</Link>
          <Link to="/me">Tài khoản</Link></nav>}
      <div className="shop-header-actions">
        <Link to="/me/orders" className="header-icon" aria-label="Đơn hàng của tôi"><Icon name="bag" /></Link>
        <Link to="/checkout" className="header-icon" aria-label="Giỏ hàng"><Icon name="cart" /></Link>
      </div>
    </div></header>
    <main className="shop-main">{children}</main>
    <footer className="shop-footer"><div className="shop-footer-inner">
      <div><strong className="brand">FORME</strong><p>Cửa hàng trực tuyến · Bản chạy trên máy cá nhân</p></div>
      <ul>
        <li><Icon name="truck" size={18} /> Giao hàng toàn quốc</li>
        <li><Icon name="return" size={18} /> Đổi trả trong 7 ngày</li>
        <li><Icon name="shield" size={18} /> Thanh toán mô phỏng an toàn</li>
      </ul>
    </div></footer>
  </div>
}

export function CheckoutSteps({ current }: { current: 1 | 2 | 3 | 4 }) {
  const steps = ['Giỏ hàng', 'Giao hàng', 'Thanh toán', 'Hoàn tất']
  return <ol className="checkout-steps" aria-label="Các bước đặt hàng">
    {steps.map((label, index) => {
      const step = index + 1
      const state = step < current ? 'done' : step === current ? 'current' : 'todo'
      return <li key={label} className={state} aria-current={state === 'current' ? 'step' : undefined}>
        <span className="step-dot">{state === 'done' ? <Icon name="check" size={14} /> : step}</span>{label}</li>
    })}
  </ol>
}
