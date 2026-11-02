import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ManagerOrdersPage from './ManagerOrdersPage'
import OrderDetailPage from './OrderDetailPage'
import SupportOrdersPage from './SupportOrdersPage'
import { getOrder, getShipment, type OrderDetail, type ShipmentTracking } from './api/orders'
import { assignDeliveryStaff, cancelCodOrder, confirmCodOrder, getManagerOrder, listDeliveryStaff, listManagerOrders,
  listPendingCodOrders, type StaffOrder, type StaffOrderSummary } from './api/staffOrders'

vi.mock('./api/orders', () => ({ getOrder: vi.fn(), getShipment: vi.fn(), cancelOrder: vi.fn(), retryPayment: vi.fn(),
  simulatePayment: vi.fn() }))
vi.mock('./api/staffOrders', () => ({ listPendingCodOrders: vi.fn(), confirmCodOrder: vi.fn(), cancelCodOrder: vi.fn(),
  listManagerOrders: vi.fn(), getManagerOrder: vi.fn(), listDeliveryStaff: vi.fn(), assignDeliveryStaff: vi.fn() }))

const item = { id: 1, productId: 1, sku: 'EL-002', name: 'Bàn phím K68', unitPrice: 650000, quantity: 1,
  discountAmount: 0, lineTotal: 650000 }
const shipment: ShipmentTracking = { id: 9, trackingNumber: 'TRK-OD261102-ABC123', status: 'ASSIGNED',
  deliveryStaffName: 'Hoàng Quốc Nam', packedAt: '2026-11-02T03:00:00Z', assignedAt: '2026-11-02T04:00:00Z',
  codAmount: 680000, codCollectedAt: null, attempts: [] }

function staffOrder(overrides: Partial<StaffOrder> = {}): StaffOrder {
  return { id: 7, orderCode: 'OD261102-ABC123', status: 'PENDING_CONFIRMATION', paymentMethod: 'COD', paymentStatus: 'UNPAID',
    customerName: 'Nguyễn Thị Lan', customerEmail: 'lan@example.test', recipientName: 'Lan', recipientPhone: '0900000011',
    shippingAddress: '12 Hồng Hà, Hà Nội', subtotal: 650000, discountAmount: 0, shippingFee: 30000, totalAmount: 680000,
    voucherCode: null, note: null, createdAt: '2026-11-02T02:00:00Z', confirmedAt: null, confirmedBy: null,
    cancelledAt: null, items: [item], shipment: null, assignable: false, ...overrides }
}

function summary(overrides: Partial<StaffOrderSummary> = {}): StaffOrderSummary {
  return { id: 7, orderCode: 'OD261102-ABC123', status: 'READY_FOR_DELIVERY', paymentMethod: 'COD', paymentStatus: 'UNPAID',
    customerName: 'Nguyễn Thị Lan', recipientPhone: '0900000011', totalAmount: 680000, itemCount: 1,
    createdAt: '2026-11-02T02:00:00Z', shipmentStatus: 'READY', deliveryStaffName: null, ...overrides }
}

const page = <T,>(items: T[]) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0 })
const httpError = (status: number, code: string) =>
  Object.assign(new Error(`HTTP ${status}`), { isAxiosError: true, response: { status, data: { code } } })

describe('Support COD confirmation screen', () => {
  beforeEach(() => { vi.resetAllMocks() })
  afterEach(cleanup)

  it('confirms a pending COD order with an internal note', async () => {
    vi.mocked(listPendingCodOrders).mockResolvedValueOnce(page([staffOrder()])).mockResolvedValueOnce(page([]))
    vi.mocked(confirmCodOrder).mockResolvedValue(staffOrder({ status: 'CONFIRMED' }))
    render(<SupportOrdersPage />)

    expect(await screen.findByText('OD261102-ABC123')).toBeInTheDocument()
    expect(screen.getByText('Bàn phím K68')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Ghi chú nội bộ (không bắt buộc)'), { target: { value: 'Đã gọi khách' } })
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận đơn' }))

    expect(await screen.findByText(/Đã xác nhận đơn OD261102-ABC123/)).toBeInTheDocument()
    expect(confirmCodOrder).toHaveBeenCalledWith(7, 'Đã gọi khách')
    expect(await screen.findByText('Không có đơn COD nào chờ xác nhận.')).toBeInTheDocument()
  })

  it('requires a reason before cancelling an order', async () => {
    vi.mocked(listPendingCodOrders).mockResolvedValueOnce(page([staffOrder()])).mockResolvedValueOnce(page([]))
    vi.mocked(cancelCodOrder).mockResolvedValue(staffOrder({ status: 'CANCELLED' }))
    render(<SupportOrdersPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Hủy đơn' }))
    expect(screen.getByRole('button', { name: 'Xác nhận hủy đơn' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Lý do hủy (bắt buộc)'), { target: { value: 'Không liên lạc được khách' } })
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận hủy đơn' }))

    expect(await screen.findByText(/Đã hủy đơn OD261102-ABC123/)).toBeInTheDocument()
    expect(cancelCodOrder).toHaveBeenCalledWith(7, 'Không liên lạc được khách')
  })

  it('explains an order that is no longer waiting', async () => {
    vi.mocked(listPendingCodOrders).mockResolvedValue(page([staffOrder()]))
    vi.mocked(confirmCodOrder).mockRejectedValue(httpError(409, 'ORDER_NOT_PENDING_CONFIRMATION'))
    render(<SupportOrdersPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận đơn' }))

    expect(await screen.findByText('Đơn này không còn chờ xác nhận. Hãy tải lại danh sách.')).toBeInTheDocument()
  })
})

describe('Manager orders screen', () => {
  beforeEach(() => { vi.resetAllMocks() })
  afterEach(cleanup)

  it('filters orders and assigns a packed order to a shipper', async () => {
    vi.mocked(listManagerOrders).mockResolvedValue(page([summary()]))
    vi.mocked(getManagerOrder).mockResolvedValue(staffOrder({ status: 'READY_FOR_DELIVERY', assignable: true,
      shipment: { ...shipment, status: 'READY', deliveryStaffName: null, assignedAt: null } }))
    vi.mocked(listDeliveryStaff).mockResolvedValue([{ id: 21, fullName: 'Hoàng Quốc Nam', email: 'delivery1@oss.example.test',
      activeShipments: 2 }])
    vi.mocked(assignDeliveryStaff).mockResolvedValue(staffOrder({ status: 'ASSIGNED', shipment }))
    render(<ManagerOrdersPage />)

    expect(await screen.findByText('OD261102-ABC123')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: 'Chờ phân công' }))
    await waitFor(() => expect(listManagerOrders).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'READY_FOR_DELIVERY' })))

    fireEvent.click(screen.getByRole('button', { name: 'Xem' }))
    fireEvent.change(await screen.findByLabelText('Phân công nhân viên giao hàng'), { target: { value: '21' } })
    fireEvent.click(screen.getByRole('button', { name: 'Phân công' }))

    expect(await screen.findByText('Đã phân công đơn OD261102-ABC123 cho Hoàng Quốc Nam.')).toBeInTheDocument()
    expect(assignDeliveryStaff).toHaveBeenCalledWith(7, 21)
  })

  it('searches orders and hides assignment for orders that are not packed', async () => {
    vi.mocked(listManagerOrders).mockResolvedValue(page([summary({ status: 'PENDING_CONFIRMATION', shipmentStatus: null })]))
    vi.mocked(getManagerOrder).mockResolvedValue(staffOrder())
    render(<ManagerOrdersPage />)
    await screen.findByText('OD261102-ABC123')

    fireEvent.change(screen.getByLabelText('Tìm đơn hàng'), { target: { value: 'lan@' } })
    await waitFor(() => expect(listManagerOrders).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'lan@' })))
    fireEvent.click(screen.getByRole('button', { name: 'Xem' }))

    expect(await screen.findByText('lan@example.test')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Phân công' })).not.toBeInTheDocument()
    expect(listDeliveryStaff).not.toHaveBeenCalled()
  })
})

describe('Customer shipment tracking', () => {
  beforeEach(() => { vi.resetAllMocks() })
  afterEach(cleanup)

  const order = (overrides: Partial<OrderDetail>): OrderDetail => ({ id: 7, orderCode: 'OD261102-ABC123', status: 'ASSIGNED',
    paymentMethod: 'COD', recipientName: 'Lan', recipientPhone: '0900000011', shippingAddress: '12 Hồng Hà, Hà Nội',
    subtotal: 650000, discountAmount: 0, shippingFee: 30000, totalAmount: 680000, voucherCode: null,
    createdAt: '2026-11-02T02:00:00Z', confirmedAt: '2026-11-02T02:30:00Z', deliveredAt: null, cancelledAt: null,
    items: [item], payment: { id: 31, attemptNumber: 1, method: 'COD', status: 'UNPAID', amount: 680000,
      simulationCode: null, expiresAt: null, paidAt: null, simulated: false },
    refund: null, paymentAttemptsUsed: 1, maxPaymentAttempts: 3, cancellable: false, paymentRetryAllowed: false, ...overrides })

  function renderOrder() {
    return render(<MemoryRouter initialEntries={['/me/orders/7']}><Routes>
      <Route path="/me/orders/:id" element={<OrderDetailPage />} /></Routes></MemoryRouter>)
  }

  it('shows the tracking number, shipper and delivery history', async () => {
    vi.mocked(getOrder).mockResolvedValue(order({ status: 'DELIVERY_FAILED' }))
    vi.mocked(getShipment).mockResolvedValue({ ...shipment, status: 'DELIVERY_FAILED', attempts: [{ attemptNumber: 1,
      status: 'FAILED', failureReason: 'Khách vắng nhà', startedAt: '2026-11-02T05:00:00Z', finishedAt: '2026-11-02T06:00:00Z' }] })
    renderOrder()

    expect(await screen.findByText('Theo dõi vận chuyển')).toBeInTheDocument()
    expect(screen.getByText('TRK-OD261102-ABC123')).toBeInTheDocument()
    expect(screen.getAllByText('Hoàng Quốc Nam')).toHaveLength(2)
    expect(screen.getByText('Lần giao 1: giao thất bại')).toBeInTheDocument()
    expect(screen.getByText('Khách vắng nhà')).toBeInTheDocument()
  })

  it('does not ask for a shipment before the order is packed', async () => {
    vi.mocked(getOrder).mockResolvedValue(order({ status: 'CONFIRMED', cancellable: true }))
    renderOrder()

    expect(await screen.findByText('Đơn hàng đã được xác nhận')).toBeInTheDocument()
    expect(getShipment).not.toHaveBeenCalled()
    expect(screen.queryByText('Theo dõi vận chuyển')).not.toBeInTheDocument()
  })
})
