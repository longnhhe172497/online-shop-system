import { api } from './client'
import type { Page } from './products'

export type OrderStatus = 'AWAITING_PAYMENT' | 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'READY_FOR_DELIVERY'
  | 'ASSIGNED' | 'DELIVERING' | 'DELIVERED' | 'DELIVERY_FAILED' | 'RETURNED_TO_WAREHOUSE' | 'CANCELLED'
export type PaymentMethod = 'SIMULATED_QR' | 'COD'

export interface PreviewLine {
  productId: number; sku: string; name: string; unitPrice: number; quantity: number
  availableQuantity: number; lineSubtotal: number; problem: 'PRODUCT_UNAVAILABLE' | 'INSUFFICIENT_STOCK' | null
}
export interface CheckoutPreview {
  items: PreviewLine[]; subtotal: number; discountAmount: number; shippingFee: number; totalAmount: number
  voucherCode: string | null; canPlaceOrder: boolean
}
export interface OrderSummary {
  id: number; orderCode: string; status: OrderStatus; paymentMethod: PaymentMethod; totalAmount: number
  itemCount: number; createdAt: string
}
export interface OrderItem {
  id: number; productId: number; sku: string; name: string; unitPrice: number; quantity: number
  discountAmount: number; lineTotal: number
}
export interface Payment {
  id: number; attemptNumber: number; method: PaymentMethod
  status: 'PENDING' | 'UNPAID' | 'PAID' | 'FAILED' | 'EXPIRED' | 'CANCELLED'
  amount: number; simulationCode: string | null; expiresAt: string | null; paidAt: string | null; simulated: boolean
}
export interface Refund { amount: number; status: 'PENDING' | 'COMPLETED' | 'FAILED'; reason: string; simulated: boolean }
export interface OrderDetail {
  id: number; orderCode: string; status: OrderStatus; paymentMethod: PaymentMethod
  recipientName: string; recipientPhone: string; shippingAddress: string
  subtotal: number; discountAmount: number; shippingFee: number; totalAmount: number; voucherCode: string | null
  createdAt: string; confirmedAt: string | null; deliveredAt: string | null; cancelledAt: string | null
  items: OrderItem[]; payment: Payment | null; refund: Refund | null
  paymentAttemptsUsed: number; maxPaymentAttempts: number; cancellable: boolean; paymentRetryAllowed: boolean
}

export async function previewCheckout(voucherCode?: string) {
  const { data } = await api.post<CheckoutPreview>('/orders/preview', { voucherCode: voucherCode || null })
  return data
}

export async function placeOrder(input: { addressId: number; paymentMethod: PaymentMethod; voucherCode?: string }) {
  const { data } = await api.post<OrderDetail>('/orders', { ...input, voucherCode: input.voucherCode || null })
  return data
}

export async function listOrders(page = 0, size = 10) {
  const { data } = await api.get<Page<OrderSummary>>('/orders', { params: { page, size } })
  return data
}

export async function getOrder(id: number) {
  const { data } = await api.get<OrderDetail>(`/orders/${id}`)
  return data
}

export async function cancelOrder(id: number, reason: string) {
  const { data } = await api.post<OrderDetail>(`/orders/${id}/cancel`, { reason: reason || null })
  return data
}

export async function retryPayment(orderId: number) {
  const { data } = await api.post<OrderDetail>(`/orders/${orderId}/payment-attempts`)
  return data
}

/** Development simulation only: no real money is transferred. */
export async function simulatePayment(attemptId: number, outcome: 'SUCCESS' | 'FAILURE') {
  const { data } = await api.post<OrderDetail>(`/payment-attempts/${attemptId}/simulate`, { outcome })
  return data
}
