const TICKET_STATUS: Record<string, string> = {
  OPEN: 'Mới tiếp nhận', IN_PROGRESS: 'Đang xử lý', RESOLVED: 'Đã giải quyết', CLOSED: 'Đã đóng',
}

const RETURN_STATUS: Record<string, string> = {
  REQUESTED: 'Mới yêu cầu', UNDER_REVIEW: 'Đang xem xét', APPROVED: 'Đã chấp thuận',
  REJECTED: 'Đã từ chối', MORE_INFO_REQUIRED: 'Cần bổ sung thông tin',
  RETURN_AUTHORIZED: 'Được phép gửi trả', WAITING_FOR_RETURN: 'Chờ khách gửi hàng', RECEIVED: 'Đã nhận hàng',
  INSPECTED: 'Đã kiểm định', REFUND_PENDING: 'Chờ hoàn tiền', REFUNDED: 'Đã hoàn tiền',
  PARTIALLY_REFUNDED: 'Đã hoàn một phần', COMPLETED: 'Hoàn tất', CLOSED: 'Đã đóng', CANCELLED: 'Đã hủy',
}

const REFUND_STATUS: Record<string, string> = {
  PENDING: 'Chờ xử lý', PROCESSING: 'Đang xử lý', COMPLETED: 'Đã hoàn tất', FAILED: 'Không thành công',
}

const REFUND_METHOD: Record<string, string> = {
  ORIGINAL_PAYMENT: 'Phương thức thanh toán ban đầu', BANK_TRANSFER: 'Chuyển khoản ngân hàng',
  CASH: 'Tiền mặt', MANUAL: 'Xử lý thủ công', QR: 'Thanh toán QR', COD: 'Thanh toán khi nhận hàng',
  SIMULATED_QR: 'Hoàn tiền qua QR mô phỏng', MANUAL_COD: 'Hoàn tiền COD thủ công',
}

const REFUND_REASON: Record<string, string> = {
  CUSTOMER_RETURN: 'Khách hàng trả sản phẩm', CANCELLED_PAID_ORDER: 'Hủy đơn đã thanh toán',
  FAILED_PREPAID_DELIVERY: 'Giao đơn trả trước không thành công',
}

export function ticketStatusLabel(status: string) { return TICKET_STATUS[status] ?? 'Chưa xác định' }
export function returnStatusLabel(status: string) { return RETURN_STATUS[status] ?? 'Chưa xác định' }
export function refundStatusLabel(status: string) { return REFUND_STATUS[status] ?? 'Chưa xác định' }
export function refundMethodLabel(method: string) { return REFUND_METHOD[method] ?? 'Phương thức khác' }
export function refundReasonLabel(reason: string) { return REFUND_REASON[reason] ?? 'Lý do khác' }
export function inspectionResultLabel(result: string) {
  if (result === 'ELIGIBLE') return 'Đủ điều kiện'
  if (result === 'NOT_ELIGIBLE') return 'Không đủ điều kiện'
  return 'Chưa xác định'
}

export function ticketStatusTone(status: string) {
  if (status === 'RESOLVED') return 'lg-ok'
  if (status === 'CLOSED') return 'lg-muted'
  if (status === 'IN_PROGRESS') return 'lg-warn'
  return 'lg-info'
}

export function returnStatusTone(status: string) {
  if (status === 'COMPLETED' || status === 'APPROVED' || status === 'INSPECTED'
    || status === 'REFUNDED' || status === 'PARTIALLY_REFUNDED') return 'lg-ok'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'lg-bad'
  if (status === 'CLOSED') return 'lg-muted'
  if (status === 'UNDER_REVIEW' || status === 'REFUND_PENDING') return 'lg-warn'
  return 'lg-info'
}

export function refundStatusTone(status: string) {
  if (status === 'COMPLETED') return 'lg-ok'
  if (status === 'FAILED') return 'lg-bad'
  if (status === 'PROCESSING') return 'lg-warn'
  return 'lg-info'
}
