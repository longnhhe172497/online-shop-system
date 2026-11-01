import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CartPage from './CartPage'
import { getCart, removeCartItem, updateCartItem, type CartLine } from './api/cart'
import { getMe } from './api/auth'
import { announceCartCount } from './cartEvents'

vi.mock('./api/cart', () => ({ getCart: vi.fn(), addCartItem: vi.fn(), updateCartItem: vi.fn(), removeCartItem: vi.fn() }))
vi.mock('./api/auth', () => ({ getMe: vi.fn(), logout: vi.fn() }))

const line: CartLine = { id: 7, productId: 1, sku: 'SKU-1', name: 'Áo sơ mi xanh', imageUrl: null,
  unitPrice: 100000, quantity: 2, availableQuantity: 5, lineTotal: 200000, warning: null }

function cartOf(items: CartLine[], subtotal: number) {
  return { items, itemCount: items.reduce((sum, item) => sum + item.quantity, 0), subtotal }
}

const customer = { id: 1, email: 'khach@example.com', fullName: 'Khách hàng', role: 'CUSTOMER' }

function renderPage() {
  return render(<MemoryRouter initialEntries={['/cart']}><CartPage /></MemoryRouter>)
}

describe('CartPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getMe).mockRejectedValue(new Error('Không có phiên'))
    vi.mocked(getCart).mockResolvedValue(cartOf([line], 200000))
  })
  afterEach(cleanup)

  it('shows lines and subtotal, and asks a guest to sign in before checkout', async () => {
    renderPage()
    expect(await screen.findByText('Áo sơ mi xanh')).toBeInTheDocument()
    expect(screen.getAllByText('200.000 ₫')).toHaveLength(2)
    expect(screen.getByRole('link', { name: 'ĐĂNG NHẬP ĐỂ THANH TOÁN' })).toHaveAttribute('href', '/auth')
    expect(await screen.findByRole('link', { name: 'Giỏ hàng (2)' })).toHaveAttribute('href', '/cart')
  })

  it('sends a signed-in customer to checkout and shows the orders link', async () => {
    vi.mocked(getMe).mockResolvedValue(customer)
    renderPage()
    expect(await screen.findByRole('link', { name: 'THANH TOÁN' })).toHaveAttribute('href', '/checkout')
    expect(await screen.findByRole('link', { name: 'Đơn hàng' })).toHaveAttribute('href', '/me/orders')
  })

  it('changes the quantity of a line', async () => {
    vi.mocked(updateCartItem).mockResolvedValue(cartOf([{ ...line, quantity: 3, lineTotal: 300000 }], 300000))
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Tăng số lượng' }))
    expect(updateCartItem).toHaveBeenCalledWith(7, 3)
    expect(await screen.findAllByText('300.000 ₫')).toHaveLength(2)
  })

  it('asks for confirmation before removing a line', async () => {
    vi.mocked(removeCartItem).mockResolvedValue(cartOf([], 0))
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Xóa' }))
    expect(removeCartItem).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('group', { name: 'Xác nhận xóa' })).getByRole('button', { name: 'Xóa' }))
    await waitFor(() => expect(removeCartItem).toHaveBeenCalledWith(7))
    expect(await screen.findByText('Giỏ hàng đang trống.')).toBeInTheDocument()
  })

  it('blocks checkout while a line is flagged', async () => {
    vi.mocked(getMe).mockResolvedValue(customer)
    vi.mocked(getCart).mockResolvedValue(cartOf([line,
      { ...line, id: 8, productId: 2, name: 'Quần ống rộng', warning: 'PRODUCT_UNAVAILABLE' }], 200000))
    renderPage()
    expect(await screen.findByText('Sản phẩm này đã ngừng bán. Hãy xóa khỏi giỏ để tiếp tục.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'THANH TOÁN' })).toBeDisabled())
  })

  it('tells a staff account that the cart is for customers', async () => {
    vi.mocked(getCart).mockRejectedValue({ isAxiosError: true,
      response: { status: 403, data: { code: 'CART_CUSTOMER_ONLY' } } })
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Tài khoản nội bộ không dùng được giỏ hàng')
  })

  it('updates the header badge when the cart changes elsewhere', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: 'Giỏ hàng (2)' })).toBeInTheDocument()
    announceCartCount(5)
    expect(await screen.findByRole('link', { name: 'Giỏ hàng (5)' })).toBeInTheDocument()
  })

  it('links staff to their role page instead of /internal and hides the cart', async () => {
    vi.mocked(getMe).mockResolvedValue({ id: 5, email: 'ql@example.com', fullName: 'Quản lý', role: 'MANAGER' })
    renderPage()
    expect(await screen.findByRole('link', { name: 'Khu vực nội bộ' })).toHaveAttribute('href', '/manager')
    expect(screen.queryByRole('link', { name: /^Giỏ hàng/ })).not.toBeInTheDocument()
  })
})
