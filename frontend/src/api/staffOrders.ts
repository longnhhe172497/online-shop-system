import { api } from './client'
import type { OrderItem, OrderStatus, PaymentMethod, Payment, ShipmentStatusCode, ShipmentTracking } from './orders'
import type { Page } from './products'

/** Orders as Support and Manager staff see them. */
export interface StaffOrderSummary {
  id: number; orderCode: string; status: OrderStatus; paymentMethod: PaymentMethod
  paymentStatus: Payment['status'] | null; customerName: string; recipientPhone: string; totalAmount: number
  itemCount: number; createdAt: string; shipmentStatus: ShipmentStatusCode | null; deliveryStaffName: string | null
}
export interface StaffOrder {
  id: number; orderCode: string; status: OrderStatus; paymentMethod: PaymentMethod
  paymentStatus: Payment['status'] | null; customerName: string; customerEmail: string
  recipientName: string; recipientPhone: string; shippingAddress: string
  subtotal: number; discountAmount: number; shippingFee: number; totalAmount: number; voucherCode: string | null
  note: string | null; createdAt: string; confirmedAt: string | null; confirmedBy: string | null
  cancelledAt: string | null; items: OrderItem[]; shipment: ShipmentTracking | null; assignable: boolean
}
export interface DeliveryStaffOption { id: number; fullName: string; email: string; activeShipments: number }

// Support: COD confirmation (UC-SUP-001)
export async function listPendingCodOrders(page = 0, size = 20) {
  const { data } = await api.get<Page<StaffOrder>>('/support/orders/pending', { params: { page, size } })
  return data
}
export async function confirmCodOrder(id: number, note: string) {
  const { data } = await api.post<StaffOrder>(`/support/orders/${id}/confirm`, { note: note || null })
  return data
}
export async function cancelCodOrder(id: number, reason: string) {
  const { data } = await api.post<StaffOrder>(`/support/orders/${id}/cancel`, { reason })
  return data
}

// Manager: all orders and delivery assignment (UC-MNG-008, UC-MNG-009)
export async function listManagerOrders(filters: { status?: string; search?: string; page?: number; size?: number }) {
  const { data } = await api.get<Page<StaffOrderSummary>>('/manager/orders', { params: {
    status: filters.status || undefined, search: filters.search || undefined, page: filters.page ?? 0,
    size: filters.size ?? 20 } })
  return data
}
export async function getManagerOrder(id: number) {
  const { data } = await api.get<StaffOrder>(`/manager/orders/${id}`)
  return data
}
export async function listDeliveryStaff() {
  const { data } = await api.get<DeliveryStaffOption[]>('/manager/delivery-staff')
  return data
}
export async function assignDeliveryStaff(orderId: number, deliveryStaffId: number) {
  const { data } = await api.post<StaffOrder>(`/manager/orders/${orderId}/assign`, { deliveryStaffId })
  return data
}
