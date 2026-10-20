import type { OrderStatus, Payment, PaymentMethod } from './api/orders'

export const SIMULATION_NOTICE = 'Thanh toán mô phỏng — không có tiền thật được chuyển.'

export const orderStatusNames: Record<OrderStatus, string> = {
  AWAITING_PAYMENT: 'Chờ thanh toán',
  PENDING_CONFIRMATION: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  READY_FOR_DELIVERY: 'Đã đóng gói',
  ASSIGNED: 'Đã phân công giao',
  DELIVERING: 'Đang giao',
  DELIVERED: 'Đã giao',
  DELIVERY_FAILED: 'Giao không thành công',
  RETURNED_TO_WAREHOUSE: 'Đã hoàn về kho',
  CANCELLED: 'Đã hủy',
}

export const paymentMethodNames: Record<PaymentMethod, string> = {
  SIMULATED_QR: 'QR (mô phỏng)',
  COD: 'Thanh toán khi nhận hàng',
}

export const paymentStatusNames: Record<Payment['status'], string> = {
  PENDING: 'Chờ quét mã', UNPAID: 'Chưa thanh toán', PAID: 'Đã thanh toán', FAILED: 'Thất bại',
  EXPIRED: 'Hết hạn', CANCELLED: 'Đã hủy',
}

export function statusTone(status: OrderStatus) {
  if (status === 'CANCELLED' || status === 'DELIVERY_FAILED' || status === 'RETURNED_TO_WAREHOUSE') return 'danger'
  if (status === 'AWAITING_PAYMENT' || status === 'PENDING_CONFIRMATION') return 'warning'
  if (status === 'DELIVERED') return 'success'
  return 'info'
}

const vnd = new Intl.NumberFormat('vi-VN')
export function formatVnd(amount: number) {
  return `${vnd.format(amount)} ₫`
}

export function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
}
