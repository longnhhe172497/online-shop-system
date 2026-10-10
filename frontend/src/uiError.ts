import { isAxiosError } from 'axios'

const messages: Record<string, string> = {
  ADDRESS_LIMIT_REACHED: 'Bạn đã lưu tối đa số địa chỉ cho phép.',
  EMAIL_ALREADY_REGISTERED: 'Email này đã được đăng ký.',
  INVALID_CREDENTIALS: 'Email hoặc mật khẩu không đúng.',
  INVALID_RESET_TOKEN: 'Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.',
  INVALID_VERIFICATION_TOKEN: 'Liên kết xác minh không hợp lệ hoặc đã hết hạn.',
  INVALID_ROLE: 'Vai trò không hợp lệ.',
  INVALID_STATUS: 'Trạng thái không hợp lệ.',
  INVALID_SETTING: 'Giá trị thiết lập không hợp lệ hoặc vượt giới hạn.',
  EMAIL_NOT_VERIFIED: 'Email của tài khoản này chưa được xác minh.',
  LAST_ADMIN: 'Không thể xóa quyền của quản trị viên đang hoạt động cuối cùng.',
  INVALID_PAGE: 'Số trang hoặc kích thước trang không hợp lệ.',
  NOT_FOUND: 'Không tìm thấy thông tin được yêu cầu.',
  PASSWORD_MISMATCH: 'Mật khẩu nhập lại không khớp.',
  TOO_MANY_REQUESTS: 'Bạn đã thử quá nhiều lần. Vui lòng chờ rồi thử lại.',
  INVALID_CURRENT_PASSWORD: 'Mật khẩu hiện tại không đúng.',
  INVALID_INVITATION: 'Lời mời không hợp lệ hoặc đã hết hạn.',
  INVITATION_DELIVERY_FAILED: 'Không gửi được email lời mời. Hãy kiểm tra cấu hình thư rồi thử lại.',
  INVITATION_NOT_RESENDABLE: 'Chỉ gửi lại được lời mời thất bại, hết hạn hoặc kẹt trạng thái gửi.',
  INVITATION_REPLACED: 'Lời mời đã được thay bằng lời mời mới. Hãy tải lại danh sách.',
  INVITATION_NOT_REVOCABLE: 'Lời mời này không còn hiệu lực để thu hồi.',
  INVALID_DATE_RANGE: 'Khoảng ngày không hợp lệ.',
  INVALID_MFA_CHALLENGE: 'Phiên xác minh đã hết hạn. Hãy đăng nhập lại.',
  INVALID_MFA_CODE: 'Mã xác minh không đúng.',
  MFA_ALREADY_ENABLED: 'Xác minh hai bước đã được bật.',
  INVALID_EMAIL_CHANGE_TOKEN: 'Liên kết đổi email không hợp lệ hoặc đã hết hạn.',
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
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
