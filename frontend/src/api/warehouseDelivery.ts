import axios from 'axios'
import { api } from './client'

export interface PageResponse<T> { items: T[]; page: number; size: number; total: number }
// Tên trường tổng số bản ghi của com.osmshop.api.PageResponse có thể khác nhau, nên chuẩn hóa tại đây.
interface RawPage<T> { items: T[]; page: number; size: number; [key: string]: unknown }
function toPage<T>(raw: RawPage<T>): PageResponse<T> {
  const total = [raw.total, raw.totalElements, raw.totalItems, raw.totalCount, raw.count]
    .find((v): v is number => typeof v === 'number')
  return { items: raw.items, page: raw.page, size: raw.size, total: total ?? raw.items.length }
}

export type ShipmentStatus = 'PENDING_PICKUP' | 'PACKED' | 'READY_FOR_DISPATCH' | 'SHIPPING'
  | 'DELIVERED' | 'DELIVERY_FAILED' | 'RETURNED_TO_WAREHOUSE'
export type DeliveryScope = 'MINE' | 'UNASSIGNED' | 'ALL'

export interface ProductStock {
  id: number; sku: string; name: string; price: number
  onHandQty: number; reservedQty: number; availableQty: number
  reorderThreshold: number; lowStock: boolean; status: string
}
export interface InventoryTransaction {
  id: number; productId: number; type: string; quantityDelta: number; balanceAfter: number
  referenceId: string | null; notes: string | null; staffId: number | null; createdAt: string
}
export interface RestockRequest { quantity: number; referenceId?: string; notes?: string }
export interface RestockResponse { product: ProductStock; transaction: InventoryTransaction }
export interface OrderItem { productId: number; productName: string; sku: string; quantity: number }
export interface PackingOrder {
  id: number; orderCode: string; recipientName: string; recipientPhone: string; deliveryAddress: string
  totalAmount: number; paymentMethod: 'ONLINE_QR' | 'COD'; paymentStatus: string; status: string
  createdAt: string; items: OrderItem[]
}
export interface ShipmentListItem {
  id: number; orderId: number; orderCode: string; trackingNumber: string | null; carrierName: string | null
  status: ShipmentStatus; deliveryStaffId: number | null; warehouseStaffId: number | null
  recipientName: string; recipientPhone: string; deliveryAddress: string; paymentMethod: string
  orderTotal: number; codAmount: number; codCollected: boolean; deliveryNotes: string | null
  failedReason: string | null; createdAt: string; dispatchedAt: string | null; deliveredAt: string | null
}
export interface ShipmentHistory {
  id: number; previousStatus: ShipmentStatus | null; newStatus: ShipmentStatus
  note: string | null; updatedBy: number | null; createdAt: string
}
export interface ShipmentDetail { shipment: ShipmentListItem; items: OrderItem[]; history: ShipmentHistory[] }
export interface UpdateShipmentStatusRequest {
  status: 'SHIPPING' | 'DELIVERED' | 'DELIVERY_FAILED'
  note?: string; failedReason?: string; codCollected?: boolean
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { detail?: string; message?: string } | undefined
    return data?.detail ?? data?.message ?? error.message
  }
  return error instanceof Error ? error.message : 'Đã xảy ra lỗi không xác định.'
}

const WAREHOUSE = '/internal/warehouse'
const DELIVERY = '/internal/delivery'

// ---- Kho ----
export async function listProducts(params: { search?: string; lowStockOnly?: boolean; page?: number; size?: number }) {
  return toPage((await api.get<RawPage<ProductStock>>(`${WAREHOUSE}/products`, { params })).data)
}
export async function restockProduct(productId: number, body: RestockRequest) {
  return (await api.post<RestockResponse>(`${WAREHOUSE}/products/${productId}/restock`, body)).data
}
export async function listProductTransactions(productId: number, page = 0, size = 20) {
  return toPage((await api.get<RawPage<InventoryTransaction>>(`${WAREHOUSE}/products/${productId}/transactions`,
    { params: { page, size } })).data)
}
export async function listPackingOrders(page = 0, size = 20) {
  return toPage((await api.get<RawPage<PackingOrder>>(`${WAREHOUSE}/orders`, { params: { page, size } })).data)
}
export async function packOrder(orderId: number, body?: { carrierName?: string; notes?: string }) {
  return (await api.post<ShipmentDetail>(`${WAREHOUSE}/orders/${orderId}/pack`, body ?? {})).data
}

// ---- Giao hàng ----
export async function listShipments(params: { scope?: DeliveryScope; status?: ShipmentStatus; page?: number; size?: number }) {
  return toPage((await api.get<RawPage<ShipmentListItem>>(`${DELIVERY}/shipments`, { params })).data)
}
export async function getShipment(id: number) {
  return (await api.get<ShipmentDetail>(`${DELIVERY}/shipments/${id}`)).data
}
export async function updateShipmentStatus(id: number, body: UpdateShipmentStatusRequest) {
  return (await api.patch<ShipmentDetail>(`${DELIVERY}/shipments/${id}/status`, body)).data
}

// ---- Báo cáo ngày (bảng staff_daily_reports) ----
export type DailyReportTeam = 'warehouse' | 'delivery'
export interface DailyReport {
  id: number; staffName: string; reportDate: string; timeAgo: string
  content: string; updatedAt: string; mine: boolean
}
export async function submitDailyReport(team: DailyReportTeam, content: string) {
  return (await api.post<DailyReport>(`/internal/${team}/reports/daily`, { content })).data
}
export async function listRecentDailyReports(team: DailyReportTeam) {
  return (await api.get<DailyReport[]>(`/internal/${team}/reports/daily/recent`)).data
}
