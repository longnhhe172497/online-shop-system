import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OrderDetailPage from './OrderDetailPage'
import SupportTicketsPage from './SupportTicketsPage'
import { getOrder, getShipment, type OrderDetail } from './api/orders'
import { createReturnRequest, listMyReturns, listTicketQueue, type ReturnRequest, type SupportTicket } from './api/afterSales'

vi.mock('./SignOutButton', () => ({ default: () => null }))
vi.mock('./api/orders', () => ({ getOrder: vi.fn(), getShipment: vi.fn(), cancelOrder: vi.fn(), retryPayment: vi.fn(),
  simulatePayment: vi.fn() }))
vi.mock('./api/afterSales', () => ({ createReturnRequest: vi.fn(), listMyReturns: vi.fn(), listTicketQueue: vi.fn(),
  replyTicket: vi.fn(), updateTicketStatus: vi.fn() }))

const item = { id: 11, productId: 1, sku: 'AO-01', name: 'Áo thun trắng', unitPrice: 250000, quantity: 2,
  discountAmount: 0, lineTotal: 500000 }
const order: OrderDetail = { id: 7, orderCode: 'OD-7', status: 'DELIVERED', paymentMethod: 'SIMULATED_QR',
  recipientName: 'Lan', recipientPhone: '0900000011', shippingAddress: '12 Hồng Hà, Hà Nội', subtotal: 500000,
  discountAmount: 0, shippingFee: 0, totalAmount: 500000, voucherCode: null, createdAt: '2026-11-02T02:00:00Z',
  confirmedAt: '2026-11-02T02:30:00Z', deliveredAt: '2026-11-08T02:00:00Z', cancelledAt: null, items: [item],
  payment: { id: 31, attemptNumber: 1, method: 'SIMULATED_QR', status: 'PAID', amount: 500000, simulationCode: null,
    expiresAt: null, paidAt: '2026-11-02T02:10:00Z', simulated: true },
  refund: null, paymentAttemptsUsed: 1, maxPaymentAttempts: 3, cancellable: false, paymentRetryAllowed: false }
const request = (overrides: Partial<ReturnRequest>): ReturnRequest => ({ id: 40, orderId: 7, orderCode: 'OD-7', customerId: 5,
  customerName: 'Lan', requestType: 'REFUND_ONLY', status: 'REQUESTED', reason: 'Phai màu', evidenceUrl: null,
  decisionNote: null, createdAt: '2026-11-09T03:00:00Z', items: [{ id: 1, orderItemId: 11, productName: 'Áo thun trắng',
    sku: 'AO-01', requestedQuantity: 1, refundAmount: 250000, receivedQuantity: null, restockQuantity: null,
    inspectionResult: null, conditionNote: null }], ...overrides })

function renderOrder() {
  return render(<MemoryRouter initialEntries={['/me/orders/7']}><Routes>
    <Route path="/me/orders/:id" element={<OrderDetailPage />} /></Routes></MemoryRouter>)
}

describe('Customer follows return requests on the order page', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getOrder).mockResolvedValue(order)
    vi.mocked(getShipment).mockResolvedValue({ id: 9, trackingNumber: 'TRK-OD-7', status: 'DELIVERED', deliveryStaffName: 'Nam',
      packedAt: '2026-11-07T03:00:00Z', assignedAt: '2026-11-07T04:00:00Z', codAmount: 0, codCollectedAt: null, attempts: [] })
  })
  afterEach(cleanup)

  it('shows the Manager\'s reason for a rejection (MSG-033) and the approval message (MSG-032), only for this order', async () => {
    vi.mocked(listMyReturns).mockResolvedValue([
      request({ id: 41, status: 'REJECTED', decisionNote: 'Sản phẩm không lỗi' }),
      request({ id: 42, status: 'REFUND_PENDING' }),
      request({ id: 43, orderId: 99, orderCode: 'OD-99', status: 'REJECTED', decisionNote: 'Đơn khác' })])
    renderOrder()

    expect(await screen.findByText('Yêu cầu đã gửi')).toBeInTheDocument()
    expect(screen.getByText('Yêu cầu hoàn tiền của bạn đã bị từ chối. Lý do: Sản phẩm không lỗi.')).toBeInTheDocument()
    expect(screen.getByText('Yêu cầu hoàn tiền của bạn đã được chấp thuận.')).toBeInTheDocument()
    expect(screen.getByText('Đã từ chối')).toBeInTheDocument()
    expect(screen.queryByText(/Đơn khác/)).not.toBeInTheDocument()
  })

  it('adds a newly submitted request to the list', async () => {
    vi.mocked(listMyReturns).mockResolvedValue([])
    vi.mocked(createReturnRequest).mockResolvedValue(request({ id: 44 }))
    renderOrder()

    fireEvent.change(await screen.findByLabelText('Số lượng trả Áo thun trắng'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Lý do yêu cầu'), { target: { value: 'Phai màu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }))

    expect(await screen.findByText('Cửa hàng đang xem xét yêu cầu của bạn.')).toBeInTheDocument()
    expect(screen.getByText(/#44 · Chỉ hoàn tiền/)).toBeInTheDocument()
  })
})

describe('Support queue status filter', () => {
  const ticket: SupportTicket = { id: 16, customerId: 5, customerName: 'Lan', orderId: null, orderCode: null,
    subject: 'Đổi size', status: 'OPEN', assignedStaffId: null, createdAt: '2026-11-09T03:00:00Z',
    updatedAt: '2026-11-09T03:00:00Z', messages: [] }
  const page = (items: SupportTicket[]) => ({ items, page: 0, size: 50, totalItems: items.length, totalPages: 1 })
  beforeEach(() => { vi.resetAllMocks(); vi.mocked(listTicketQueue).mockResolvedValue(page([ticket])) })
  afterEach(cleanup)

  it('asks the server for the chosen status and for every status again with "Tất cả"', async () => {
    render(<MemoryRouter><SupportTicketsPage /></MemoryRouter>)
    expect(await screen.findByText('#16 · Đổi size')).toBeInTheDocument()
    expect(listTicketQueue).toHaveBeenLastCalledWith(undefined)

    fireEvent.change(screen.getByLabelText('Lọc theo trạng thái'), { target: { value: 'CLOSED' } })
    await waitFor(() => expect(listTicketQueue).toHaveBeenLastCalledWith('CLOSED'))

    fireEvent.change(screen.getByLabelText('Lọc theo trạng thái'), { target: { value: '' } })
    await waitFor(() => expect(listTicketQueue).toHaveBeenLastCalledWith(undefined))
  })
})
