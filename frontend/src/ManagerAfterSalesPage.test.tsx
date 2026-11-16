import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AxiosError, type AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ManagerAfterSalesPage from './ManagerAfterSalesPage'
import {
  completeRefund, createFailedDeliveryRefund, decideReturn, getDashboard, getTransactionReport, listFailedDeliveryOrders,
  listRefunds, listReturns, listStaffDailyReports, type DashboardView, type ReturnRequest,
} from './api/afterSales'

vi.mock('./SignOutButton', () => ({ default: () => null }))
vi.mock('./api/afterSales', () => ({
  completeRefund: vi.fn(), createFailedDeliveryRefund: vi.fn(), decideReturn: vi.fn(),
  downloadTransactionReport: vi.fn(), getDashboard: vi.fn(), getTransactionReport: vi.fn(),
  listFailedDeliveryOrders: vi.fn(), listRefunds: vi.fn(), listReturns: vi.fn(), listStaffDailyReports: vi.fn(),
}))

const dashboard: DashboardView = {
  summary: { from: '2026-10-11', to: '2026-11-09', orderCount: 4, paidOrderCount: 3, grossRevenue: 900000,
    refundedAmount: 100000, refundCount: 1, daily: [
      { date: '2026-11-08', orderCount: 2, paidOrderCount: 1, grossRevenue: 300000, refundedAmount: 0, refundCount: 0 },
      { date: '2026-11-09', orderCount: 2, paidOrderCount: 2, grossRevenue: 600000, refundedAmount: 100000, refundCount: 1 }] },
  topProducts: [{ productId: 1, sku: 'AO-01', name: 'Áo sơ mi xanh', unitsSold: 5, revenue: 650000 }],
  lowStock: [{ productId: 2, sku: 'VAY-02', name: 'Váy cam', available: 0, reorderThreshold: 3 }],
  pendingActions: { returnRequestsToDecide: 2, pendingRefunds: 1, unassignedOpenTickets: 3,
    deliveryFailedOrders: 0, paidQrReturnedWithoutRefund: 1 },
}
const request: ReturnRequest = { id: 7, orderId: 3, orderCode: 'FM-7', customerId: 5, customerName: 'Lan',
  requestType: 'REFUND_ONLY', status: 'REQUESTED', reason: 'Không vừa', evidenceUrl: null, decisionNote: null,
  createdAt: '2026-11-08T03:00:00Z', items: [] }
const page = <T,>(items: T[]) => ({ items, page: 0, size: 50, totalItems: items.length, totalPages: 1 })

function renderPage() {
  return render(<MemoryRouter><ManagerAfterSalesPage /></MemoryRouter>)
}

describe('Manager after-sales and reporting', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getDashboard).mockResolvedValue(dashboard)
    vi.mocked(listReturns).mockResolvedValue(page([request]))
    vi.mocked(listRefunds).mockResolvedValue(page([]))
    vi.mocked(listFailedDeliveryOrders).mockResolvedValue([])
    vi.mocked(listStaffDailyReports).mockResolvedValue([])
  })
  afterEach(cleanup)

  it('opens on the business overview with KPIs, top sellers, low stock and pending actions', async () => {
    renderPage()
    expect(await screen.findByText('Doanh thu thuần')).toBeInTheDocument()
    expect(screen.getByText('Áo sơ mi xanh')).toBeInTheDocument()
    expect(screen.getByText('Còn 0')).toBeInTheDocument()
    expect(screen.getByText('Yêu cầu trả hàng chờ quyết định')).toBeInTheDocument()
    expect(getDashboard).toHaveBeenCalledTimes(1)
  })

  it('requires a note before rejecting a return request', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('tab', { name: 'Yêu cầu trả hàng' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Từ chối' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Vui lòng nhập ghi chú lý do từ chối.')
    expect(decideReturn).not.toHaveBeenCalled()

    vi.mocked(decideReturn).mockResolvedValue({ ...request, status: 'REJECTED' })
    fireEvent.change(screen.getByRole('textbox', { name: 'Ghi chú quyết định cho yêu cầu #7' }),
      { target: { value: 'Sản phẩm đã qua sử dụng' } })
    fireEvent.click(screen.getByRole('button', { name: 'Từ chối' }))
    await waitFor(() => expect(decideReturn).toHaveBeenCalledWith(7, false, 'Sản phẩm đã qua sử dụng'))
  })

  it('creates a refund for a paid QR order returned after failed delivery', async () => {
    vi.mocked(listFailedDeliveryOrders).mockResolvedValue([{ orderId: 11, orderCode: 'FM-11', customerName: 'Minh',
      amount: 450000, returnedAt: '2026-11-09T02:00:00Z' }])
    vi.mocked(createFailedDeliveryRefund).mockResolvedValue({ id: 3, orderId: 11, returnRequestId: null,
      orderCode: 'FM-11', reason: 'FAILED_PREPAID_DELIVERY', method: 'SIMULATED_QR', status: 'PENDING',
      amount: 450000, createdAt: '2026-11-09T02:00:00Z', completedAt: null })
    renderPage()
    fireEvent.click(await screen.findByRole('tab', { name: 'Hoàn tiền' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo hoàn tiền' }))
    await waitFor(() => expect(createFailedDeliveryRefund).toHaveBeenCalledWith(11))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã tạo khoản hoàn tiền cho đơn FM-11.')
    expect(completeRefund).not.toHaveBeenCalled()
  })

  it('shows staff daily reports for the chosen date and an empty-state message', async () => {
    vi.mocked(listStaffDailyReports).mockResolvedValueOnce([{ staffName: 'Hùng',
      role: 'WAREHOUSE', reportDate: '2026-11-09', content: 'Đóng gói 12 đơn', updatedAt: '2026-11-09T10:00:00Z' }])
    renderPage()
    fireEvent.click(await screen.findByRole('tab', { name: 'Báo cáo nhân viên' }))
    expect(await screen.findByText('Đóng gói 12 đơn')).toBeInTheDocument()
    expect(screen.getByText('Nhân viên kho')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Ngày báo cáo'), { target: { value: '2026-11-01' } })
    expect(await screen.findByText('Không có báo cáo hằng ngày cho ngày đã chọn.')).toBeInTheDocument()
    expect(listStaffDailyReports).toHaveBeenLastCalledWith('2026-11-01')
  })

  it('filters the day\'s staff reports by staff member', async () => {
    vi.mocked(listStaffDailyReports).mockResolvedValueOnce([
      { staffName: 'Hùng', role: 'WAREHOUSE', reportDate: '2026-11-09', content: 'Đóng gói 12 đơn', updatedAt: '2026-11-09T10:00:00Z' },
      { staffName: 'Nam', role: 'DELIVERY', reportDate: '2026-11-09', content: 'Giao 8 đơn', updatedAt: '2026-11-09T11:00:00Z' }])
    renderPage()
    fireEvent.click(await screen.findByRole('tab', { name: 'Báo cáo nhân viên' }))
    expect(await screen.findByText('Giao 8 đơn')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Lọc theo nhân viên'), { target: { value: 'Hùng' } })
    expect(screen.getByText('Đóng gói 12 đơn')).toBeInTheDocument()
    expect(screen.queryByText('Giao 8 đơn')).not.toBeInTheDocument()
    expect(screen.getByText('1 báo cáo')).toBeInTheDocument()
  })

  it('clears the previous figures when the report period is rejected', async () => {
    vi.mocked(getTransactionReport).mockResolvedValueOnce(dashboard.summary)
    const detail = 'Khoảng thời gian báo cáo không hợp lệ (tối đa 366 ngày).'
    vi.mocked(getTransactionReport).mockRejectedValueOnce(new AxiosError('Bad Request', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 400, data: { code: 'INVALID_REPORT_RANGE', detail } } as AxiosResponse))
    renderPage()
    fireEvent.click(await screen.findByRole('tab', { name: 'Báo cáo giao dịch' }))
    expect(await screen.findByText('Tổng quan giao dịch')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Xem báo cáo' }))
    expect(await screen.findByText(detail)).toBeInTheDocument()
    expect(screen.queryByText('Tổng quan giao dịch')).not.toBeInTheDocument()
  })
})
