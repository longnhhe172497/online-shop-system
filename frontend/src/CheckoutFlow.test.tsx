import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CheckoutPage from './CheckoutPage'
import OrderDetailPage from './OrderDetailPage'
import VoucherManagementPage from './VoucherManagementPage'
import { cancelOrder, getOrder, placeOrder, previewCheckout, simulatePayment, type CheckoutPreview,
  type OrderDetail } from './api/orders'
import { listAddresses } from './api/profile'
import { getMe } from './api/auth'
import { createVoucher, listVouchers } from './api/vouchers'

vi.mock('./api/orders', () => ({ previewCheckout: vi.fn(), placeOrder: vi.fn(), listOrders: vi.fn(), getOrder: vi.fn(),
  cancelOrder: vi.fn(), retryPayment: vi.fn(), simulatePayment: vi.fn() }))
vi.mock('./api/profile', () => ({ listAddresses: vi.fn() }))
vi.mock('./api/auth', () => ({ getMe: vi.fn() }))
vi.mock('./api/vouchers', () => ({ listVouchers: vi.fn(), createVoucher: vi.fn(), updateVoucher: vi.fn(),
  deleteVoucher: vi.fn() }))

const quote: CheckoutPreview = {
  items: [{ productId: 1, sku: 'EL-002', name: 'Bàn phím K68', unitPrice: 650000, quantity: 1, availableQuantity: 9,
    lineSubtotal: 650000, problem: null }],
  subtotal: 650000, discountAmount: 0, shippingFee: 30000, totalAmount: 680000, voucherCode: null, canPlaceOrder: true,
}

function order(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return { id: 7, orderCode: 'OD261015-ABC123', status: 'AWAITING_PAYMENT', paymentMethod: 'SIMULATED_QR',
    recipientName: 'Lan', recipientPhone: '0900000011', shippingAddress: '12 Hồng Hà, Hà Nội',
    subtotal: 650000, discountAmount: 0, shippingFee: 30000, totalAmount: 680000, voucherCode: null,
    createdAt: '2026-10-15T08:00:00Z', confirmedAt: null, deliveredAt: null, cancelledAt: null,
    items: [{ id: 1, productId: 1, sku: 'EL-002', name: 'Bàn phím K68', unitPrice: 650000, quantity: 1,
      discountAmount: 0, lineTotal: 650000 }],
    payment: { id: 31, attemptNumber: 1, method: 'SIMULATED_QR', status: 'PENDING', amount: 680000,
      simulationCode: 'SIM-TEST', expiresAt: new Date(Date.now() + 10 * 60000).toISOString(), paidAt: null, simulated: true },
    refund: null, paymentAttemptsUsed: 1, maxPaymentAttempts: 3, cancellable: true, paymentRetryAllowed: false,
    ...overrides }
}

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/checkout" element={<CheckoutPage />} />
    <Route path="/me/orders/:id" element={<OrderDetailPage />} />
  </Routes></MemoryRouter>)
}

describe('Checkout and payment', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getMe).mockResolvedValue({ id: 1, email: 'test@example.com', fullName: 'Test', role: 'CUSTOMER' })
    vi.mocked(listAddresses).mockResolvedValue([{ id: 5, recipientName: 'Lan', phone: '0900000011',
      addressLine: '12 Hồng Hà', ward: 'Phúc Xá', district: 'Ba Đình', province: 'Hà Nội', isDefault: true }])
  })
  afterEach(cleanup)

  it('applies a voucher and places a QR order with the default address', async () => {
    vi.mocked(previewCheckout).mockResolvedValueOnce(quote).mockResolvedValueOnce({ ...quote, discountAmount: 50000,
      totalAmount: 630000, voucherCode: 'SHOP50' })
    vi.mocked(placeOrder).mockResolvedValue(order())
    vi.mocked(getOrder).mockResolvedValue(order())
    renderAt('/checkout')

    expect(await screen.findByText('Bàn phím K68')).toBeInTheDocument()
    expect(screen.getByText('680.000 ₫')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Mã giảm giá'), { target: { value: 'shop50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Áp dụng' }))
    expect(await screen.findByText('630.000 ₫')).toBeInTheDocument()
    expect(previewCheckout).toHaveBeenLastCalledWith('shop50')

    fireEvent.click(screen.getByRole('button', { name: 'Đặt hàng' }))
    await waitFor(() => expect(placeOrder).toHaveBeenCalledWith({ addressId: 5, paymentMethod: 'SIMULATED_QR',
      voucherCode: 'SHOP50' }))
    expect(await screen.findByText('Hoàn tất thanh toán để giữ đơn hàng')).toBeInTheDocument()
    expect(screen.getAllByText('OD261015-ABC123').length).toBeGreaterThan(0)
    expect(screen.getByText(/không có tiền thật được chuyển/)).toBeInTheDocument()
  }, 10_000)

  it('shows the empty cart state and voucher errors in Vietnamese', async () => {
    vi.mocked(previewCheckout).mockResolvedValue({ ...quote, items: [], canPlaceOrder: false })
    renderAt('/checkout')
    expect(await screen.findByText('Giỏ hàng đang trống')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đặt hàng' })).not.toBeInTheDocument()
  })

  it('asks for login only when the session is missing', async () => {
    const httpError = (status: number) => Object.assign(new Error('HTTP ' + status),
      { isAxiosError: true, response: { status, data: {} } })
    vi.mocked(previewCheckout).mockRejectedValueOnce(httpError(401))
    renderAt('/checkout')
    expect(await screen.findByText('Bạn cần đăng nhập')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/auth?next=/checkout')
    cleanup()

    vi.mocked(previewCheckout).mockRejectedValueOnce(httpError(404)).mockResolvedValueOnce(quote)
    renderAt('/checkout')
    expect(await screen.findByText('Không tải được giỏ hàng')).toBeInTheDocument()
    expect(screen.queryByText('Bạn cần đăng nhập')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Bàn phím K68')).toBeInTheDocument()
  })

  it.each(['before', 'after'])('ignores an obsolete forbidden response %s a successful StrictMode load', async (timing) => {
    let rejectOld!: (reason: unknown) => void
    let resolveCurrent!: (value: CheckoutPreview) => void
    vi.mocked(previewCheckout)
      .mockImplementationOnce(() => new Promise((_, reject) => { rejectOld = reject }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveCurrent = resolve }))
    render(<StrictMode><MemoryRouter><CheckoutPage /></MemoryRouter></StrictMode>)
    await waitFor(() => expect(previewCheckout).toHaveBeenCalledTimes(2))
    const reject = () => rejectOld(Object.assign(new Error('Forbidden'),
      { isAxiosError: true, response: { status: 403 } }))
    if (timing === 'before') await act(async () => { reject() })
    await act(async () => { resolveCurrent(quote) })
    if (timing === 'after') await act(async () => { reject() })
    expect(await screen.findByRole('button', { name: 'Đặt hàng' })).toBeEnabled()
    expect(screen.queryByText('Chỉ tài khoản khách hàng được đặt hàng')).not.toBeInTheDocument()
    expect(getMe).not.toHaveBeenCalled()
  })

  it.each(['CUSTOMER', 'ADMIN'])('checks the actual role after a forbidden response for %s', async (role) => {
    vi.mocked(previewCheckout).mockRejectedValue(Object.assign(new Error('Forbidden'),
      { isAxiosError: true, response: { status: 403 } }))
    vi.mocked(getMe).mockResolvedValue({ id: 1, email: 'test@example.com', fullName: 'Test', role })
    renderAt('/checkout')
    expect(await screen.findByText(role === 'CUSTOMER'
      ? 'Không tải được giỏ hàng' : 'Chỉ tài khoản khách hàng được đặt hàng')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đặt hàng' })).not.toBeInTheDocument()
    if (role === 'CUSTOMER') {
      expect(screen.queryByText('Chỉ tài khoản khách hàng được đặt hàng')).not.toBeInTheDocument()
      vi.mocked(previewCheckout).mockResolvedValue(quote)
      fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
      expect(await screen.findByRole('button', { name: 'Đặt hàng' })).toBeEnabled()
      expect(screen.queryByText('Không tải được giỏ hàng')).not.toBeInTheDocument()
    }
  })

  it('simulates a successful QR payment and can cancel a confirmed order', async () => {
    vi.mocked(getOrder).mockResolvedValue(order())
    vi.mocked(simulatePayment).mockResolvedValue(order({ status: 'CONFIRMED', confirmedAt: '2026-10-15T08:05:00Z',
      payment: { ...order().payment!, status: 'PAID', paidAt: '2026-10-15T08:05:00Z' } }))
    vi.mocked(cancelOrder).mockResolvedValue(order({ status: 'CANCELLED', cancellable: false,
      cancelledAt: '2026-10-15T08:10:00Z', refund: { amount: 680000, status: 'PENDING', reason: 'CANCELLED_PAID_ORDER',
        simulated: true } }))
    renderAt('/me/orders/7')

    fireEvent.click(await screen.findByRole('button', { name: 'Mô phỏng thanh toán thành công' }))
    expect(await screen.findByText(/Thanh toán mô phỏng thành công/)).toBeInTheDocument()
    expect(simulatePayment).toHaveBeenCalledWith(31, 'SUCCESS')
    expect(screen.getByText('Đơn hàng đã được xác nhận')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Hủy đơn' }))
    fireEvent.change(screen.getByLabelText('Lý do (không bắt buộc)'), { target: { value: 'Đặt nhầm' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Hủy đơn' }).at(-1)!)
    expect(await screen.findByText('Đã hủy đơn hàng.')).toBeInTheDocument()
    expect(cancelOrder).toHaveBeenCalledWith(7, 'Đặt nhầm')
    expect(screen.getByText(/đang chờ cửa hàng xử lý/)).toBeInTheDocument()
  })

  it('lets a manager create a voucher', async () => {
    vi.mocked(listVouchers).mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })
    vi.mocked(createVoucher).mockResolvedValue({ id: 1, code: 'NEW10', discountType: 'PERCENT', discountValue: 10,
      maxDiscount: 50000, minOrderAmount: 0, usageLimit: 100, usedCount: 0, validFrom: '2026-10-01T00:00:00Z',
      validUntil: '2026-11-01T00:00:00Z', status: 'ACTIVE', createdAt: '2026-10-15T00:00:00Z' })
    render(<VoucherManagementPage />)
    expect(await screen.findByText('Chưa có mã giảm giá nào.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '+ Tạo mã mới' }))
    fireEvent.change(screen.getByLabelText('Mã'), { target: { value: 'NEW10' } })
    fireEvent.change(screen.getByLabelText('Loại giảm'), { target: { value: 'PERCENT' } })
    fireEvent.change(screen.getByLabelText('Phần trăm giảm (%)'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Giảm tối đa (₫, tùy chọn)'), { target: { value: '50000' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(createVoucher).toHaveBeenCalledWith(expect.objectContaining({
      code: 'NEW10', discountType: 'PERCENT', discountValue: 10, maxDiscount: 50000, usageLimit: 100, status: 'ACTIVE' })))
    expect(await screen.findByText('Đã tạo mã giảm giá.')).toBeInTheDocument()
  })
})
