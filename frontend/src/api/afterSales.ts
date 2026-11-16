import { api } from './client'

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }
export interface ReturnItem {
  id: number; orderItemId: number; productName: string; sku: string; requestedQuantity: number
  refundAmount: number; receivedQuantity: number | null; restockQuantity: number | null
  inspectionResult: string | null; conditionNote: string | null
}
export interface ReturnRequest {
  id: number; orderId: number; orderCode: string; customerId: number; customerName: string
  requestType: string; status: string; reason: string; evidenceUrl: string | null
  decisionNote: string | null; createdAt: string; items: ReturnItem[]
}
export interface Refund {
  id: number; orderId: number; returnRequestId: number | null; orderCode: string
  reason: string; method: string; status: string; amount: number; createdAt: string; completedAt: string | null
}
export interface DailyMetric {
  date: string; orderCount: number; paidOrderCount: number; grossRevenue: number
  refundedAmount: number; refundCount: number
}
export interface ReportSummary {
  from: string; to: string; orderCount: number; paidOrderCount: number
  grossRevenue: number; refundedAmount: number; refundCount: number; daily: DailyMetric[]
}
export interface ItemInspection {
  returnRequestItemId: number; receivedQuantity: number; conditionNote: string; restockQuantity: number
}

export async function listReturns(status?: string) {
  return (await api.get<Page<ReturnRequest>>('/manager/returns', { params: { status, page: 0, size: 50 } })).data
}
export async function decideReturn(id: number, approved: boolean, note = '') {
  return (await api.post<ReturnRequest>(`/manager/returns/${id}/decision`, { approved, note })).data
}
export async function listRefunds(status = 'PENDING') {
  return (await api.get<Page<Refund>>('/manager/refunds', { params: { status, page: 0, size: 50 } })).data
}
export async function completeRefund(id: number) {
  return (await api.post<Refund>(`/manager/refunds/${id}/complete`)).data
}
export async function getTransactionReport(from: string, to: string) {
  return (await api.get<ReportSummary>('/manager/reports/summary', { params: { from, to } })).data
}
export async function downloadTransactionReport(from: string, to: string) {
  const response = await api.get<Blob>('/manager/reports/export.csv', { params: { from, to }, responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url; anchor.download = `transaction-report-${from}-${to}.csv`
  document.body.appendChild(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url)
}
export interface TopProduct { productId: number; sku: string; name: string; unitsSold: number; revenue: number }
export interface LowStockItem { productId: number; sku: string; name: string; available: number; reorderThreshold: number }
export interface PendingActions {
  returnRequestsToDecide: number; pendingRefunds: number; unassignedOpenTickets: number
  deliveryFailedOrders: number; paidQrReturnedWithoutRefund: number
}
export interface DashboardView {
  summary: ReportSummary; topProducts: TopProduct[]
  lowStock: LowStockItem[]; pendingActions: PendingActions
}
export interface StaffReportRow { staffName: string; role: string; reportDate: string; content: string; updatedAt: string }
export interface FailedDeliveryOrder {
  orderId: number; orderCode: string; customerName: string; amount: number; returnedAt: string
}

export async function getDashboard(from: string, to: string) {
  return (await api.get<DashboardView>('/manager/reports/dashboard', { params: { from, to } })).data
}
export async function listStaffDailyReports(date: string) {
  return (await api.get<StaffReportRow[]>('/manager/reports/daily-reports', { params: { date } })).data
}
export async function listFailedDeliveryOrders() {
  return (await api.get<FailedDeliveryOrder[]>('/after-sales/failed-deliveries')).data
}
export async function createFailedDeliveryRefund(orderId: number) {
  return (await api.post<Refund>(`/after-sales/failed-deliveries/${orderId}/refund`)).data
}
export async function inspectReturn(id: number, items: ItemInspection[]) {
  return (await api.post<ReturnRequest>(`/warehouse/returns/${id}/inspection`, { items })).data
}

/** The signed-in customer's own return/refund requests, newest first. */
export async function listMyReturns() {
  return (await api.get<ReturnRequest[]>('/orders/returns')).data
}

export async function createReturnRequest(orderId: number, body: {
  requestType: 'REFUND_ONLY' | 'RETURN_AND_REFUND'; reason: string; evidenceUrl?: string | null
  items: { orderItemId: number; quantity: number }[]
}) {
  return (await api.post<ReturnRequest>(`/orders/${orderId}/returns`, body)).data
}

export interface TicketMessage { id: number; senderId: number; senderName: string; message: string; createdAt: string }
export interface SupportTicket {
  id: number; customerId: number; customerName: string; orderId: number | null; orderCode: string | null
  subject: string; status: string; assignedStaffId: number | null; createdAt: string; updatedAt: string
  messages: TicketMessage[]
}
export async function listMyTickets() { return (await api.get<SupportTicket[]>('/support/tickets')).data }
export async function createTicket(body: { subject: string; orderId?: number | null; message: string }) {
  return (await api.post<SupportTicket>('/support/tickets', body)).data
}
export async function replyTicket(id: number, message: string) {
  return (await api.post<SupportTicket>(`/support/tickets/${id}/messages`, { message })).data
}
export async function listTicketQueue(status?: string) {
  return (await api.get<Page<SupportTicket>>('/support/tickets/queue', { params: { status, page: 0, size: 50 } })).data
}
export async function updateTicketStatus(id: number, status: 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED') {
  return (await api.patch<SupportTicket>(`/support/tickets/${id}/status`, { status })).data
}

export async function listWarehouseReturns() {
  return (await api.get<Page<ReturnRequest>>('/warehouse/returns', { params: { page: 0, size: 50 } })).data
}
