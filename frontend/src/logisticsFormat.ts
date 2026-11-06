import type { ShipmentStatus } from './api/warehouseDelivery'

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })

export const formatVnd = (value: number) => vnd.format(value)

export const formatDateTime = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : '—'

export const STATUS_LABELS: Record<ShipmentStatus, string> = {
  PENDING_PICKUP: 'Chờ lấy hàng',
  PACKED: 'Đã đóng gói',
  READY_FOR_DISPATCH: 'Sẵn sàng xuất kho',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  DELIVERY_FAILED: 'Giao thất bại',
  RETURNED_TO_WAREHOUSE: 'Hoàn về kho',
}
