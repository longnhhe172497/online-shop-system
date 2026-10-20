import { isAxiosError } from 'axios'

const messages: Record<string, string> = {
  ADDRESS_LIMIT_REACHED: 'Bạn đã lưu tối đa số địa chỉ cho phép.',
  EMAIL_ALREADY_REGISTERED: 'Email này đã được đăng ký.',
  INVALID_CREDENTIALS: 'Email hoặc mật khẩu không đúng.',
  INVALID_RESET_TOKEN: 'Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.',
  INVALID_VERIFICATION_TOKEN: 'Liên kết xác minh không hợp lệ hoặc đã hết hạn.',
  INVALID_ROLE: 'Vai trò không hợp lệ.',
  ROLE_NOT_EDITABLE: 'Chỉ có thể thay đổi quyền của các vai trò nhân viên nội bộ.',
  INVALID_PERMISSION: 'Danh sách quyền không hợp lệ. Hãy tải lại trang và thử lại.',
  PERMISSION_VERSION_CONFLICT: 'Quyền của vai trò này vừa được người khác thay đổi. Hãy tải lại trước khi lưu.',
  INVALID_STATUS: 'Trạng thái không hợp lệ.',
  INVALID_SETTING: 'Giá trị thiết lập không hợp lệ hoặc vượt giới hạn.',
  EMAIL_NOT_VERIFIED: 'Email của tài khoản này chưa được xác minh.',
  LAST_ADMIN: 'Không thể xóa quyền của quản trị viên đang hoạt động cuối cùng.',
  INVALID_PAGE: 'Số trang hoặc kích thước trang không hợp lệ.',
  NOT_FOUND: 'Không tìm thấy thông tin được yêu cầu.',
  PASSWORD_MISMATCH: 'Mật khẩu nhập lại không khớp.',
  TOO_MANY_REQUESTS: 'Bạn đã thử quá nhiều lần. Vui lòng chờ rồi thử lại.',
  INVALID_CURRENT_PASSWORD: 'Mật khẩu hiện tại không đúng.',
  GOOGLE_NOT_CONFIGURED: 'Đăng nhập Google chưa được cấu hình.',
  INVALID_GOOGLE_CREDENTIAL: 'Không xác minh được tài khoản Google. Vui lòng thử lại.',
  GOOGLE_LINK_REQUIRED: 'Email đã có tài khoản. Hãy đăng nhập bằng mật khẩu rồi liên kết Google trong Bảo mật tài khoản.',
  GOOGLE_EMAIL_MISMATCH: 'Bạn đã chọn tài khoản Google khác email đang đăng nhập. Hãy chọn đúng tài khoản rồi thử lại.',
  GOOGLE_ALREADY_LINKED: 'Tài khoản Google này đã được liên kết.',
  ACCOUNT_UNAVAILABLE: 'Tài khoản này hiện không thể đăng nhập.',
  INVALID_INVITATION: 'Lời mời không hợp lệ hoặc đã hết hạn.',
  INVITATION_DELIVERY_FAILED: 'Không gửi được email lời mời. Hãy kiểm tra cấu hình thư rồi thử lại.',
  INVITATION_NOT_RESENDABLE: 'Chỉ gửi lại được lời mời thất bại, hết hạn hoặc kẹt trạng thái gửi.',
  INVITATION_REPLACED: 'Lời mời đã được thay bằng lời mời mới. Hãy tải lại danh sách.',
  INVITATION_NOT_REVOCABLE: 'Lời mời này không còn hiệu lực để thu hồi.',
  INVALID_DATE_RANGE: 'Khoảng ngày không hợp lệ.',
  INVALID_MFA_CHALLENGE: 'Bước xác minh đã hết hạn. Hãy bắt đầu lại thao tác xác minh.',
  INVALID_MFA_CODE: 'Mã xác minh không đúng hoặc không còn hiệu lực. Hãy kiểm tra mã mới nhất rồi thử lại.',
  INVALID_TOTP_ENROLLMENT: 'Thời gian thiết lập ứng dụng xác thực đã hết. Hãy bắt đầu thiết lập lại.',
  MFA_ALREADY_ENABLED: 'Xác minh hai bước đã được bật. Hãy tải lại trang để xem trạng thái mới nhất.',
  MFA_NOT_ENABLED: 'Xác minh hai bước chưa được bật. Hãy tải lại trang để kiểm tra trạng thái.',
  INVALID_EMAIL_CHANGE_TOKEN: 'Liên kết đổi email không hợp lệ hoặc đã hết hạn.',
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
  CART_EMPTY: 'Giỏ hàng đang trống hoặc đơn vừa được đặt. Hãy kiểm tra lại giỏ hàng.',
  ADDRESS_NOT_FOUND: 'Không tìm thấy địa chỉ giao hàng. Hãy chọn lại địa chỉ.',
  PRODUCT_UNAVAILABLE: 'Có sản phẩm trong giỏ đã ngừng bán. Hãy bỏ sản phẩm đó khỏi giỏ.',
  INSUFFICIENT_STOCK: 'Có sản phẩm không còn đủ hàng. Hãy giảm số lượng rồi thử lại.',
  PRICE_CHANGED: 'Giá sản phẩm đã thay đổi. Hãy hủy đơn này và đặt lại với giá mới.',
  VOUCHER_INVALID: 'Mã giảm giá không tồn tại.',
  VOUCHER_NOT_ACTIVE: 'Mã giảm giá chưa bắt đầu, đã hết hạn hoặc đang tạm dừng.',
  VOUCHER_MIN_ORDER_NOT_MET: 'Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này.',
  VOUCHER_USAGE_LIMIT_REACHED: 'Mã giảm giá đã hết lượt sử dụng.',
  VOUCHER_RULE_INVALID: 'Thông tin mã giảm giá không hợp lệ (phần trăm tối đa 100, ngày kết thúc phải sau ngày bắt đầu).',
  VOUCHER_CODE_EXISTS: 'Mã giảm giá này đã tồn tại.',
  VOUCHER_IN_USE: 'Mã đã được dùng trong đơn hàng nên không thể xóa. Hãy chuyển sang Tạm dừng.',
  VOUCHER_LIMIT_BELOW_USAGE: 'Giới hạn lượt dùng không được nhỏ hơn số lượt đã dùng.',
  VOUCHER_NOT_FOUND: 'Không tìm thấy mã giảm giá.',
  ORDER_NOT_FOUND: 'Không tìm thấy đơn hàng.',
  ORDER_NOT_CANCELLABLE: 'Đơn hàng đã được đóng gói hoặc đã kết thúc nên không thể hủy.',
  PAYMENT_ATTEMPT_NOT_FOUND: 'Không tìm thấy lượt thanh toán.',
  PAYMENT_ATTEMPT_NOT_PENDING: 'Lượt thanh toán này đã kết thúc. Hãy tải lại trang.',
  PAYMENT_ATTEMPT_EXPIRED: 'Mã QR đã hết hạn. Hãy tạo mã QR mới.',
  PAYMENT_ATTEMPT_ALREADY_PENDING: 'Mã QR hiện tại vẫn còn hiệu lực.',
  PAYMENT_RETRY_NOT_ALLOWED: 'Đơn hàng này không thể tạo mã QR mới.',
  PAYMENT_ATTEMPTS_EXHAUSTED: 'Đã hết số lần thanh toán QR, đơn hàng đã bị hủy.',
}

export function getUiError(error: unknown) {
  if (isAxiosError(error)) {
    const code = error.response?.data?.code
    if (typeof code === 'string' && messages[code]) return messages[code]
    if (error.response?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    if (error.response?.status === 403) return 'Không thể xác thực yêu cầu. Hãy tải lại trang rồi thử lại.'
    if (error.response?.status === 429) return messages.TOO_MANY_REQUESTS
  }
  return 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.'
}
