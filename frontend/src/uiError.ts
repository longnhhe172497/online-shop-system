import { isAxiosError } from 'axios'

const messages: Record<string, string> = {
  ADDRESS_LIMIT_REACHED: 'Bạn đã lưu tối đa số địa chỉ cho phép.',
  EMAIL_ALREADY_REGISTERED: 'Email này đã được đăng ký.',
  INVALID_CREDENTIALS: 'Email hoặc mật khẩu không đúng.',
  INVALID_RESET_TOKEN: 'Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.',
  INVALID_VERIFICATION_TOKEN: 'Liên kết xác minh không hợp lệ hoặc đã hết hạn.',
  NOT_FOUND: 'Không tìm thấy thông tin được yêu cầu.',
  PASSWORD_MISMATCH: 'Mật khẩu nhập lại không khớp.',
}

export function getUiError(error: unknown) {
  if (isAxiosError(error)) {
    const code = error.response?.data?.code
    if (typeof code === 'string' && messages[code]) return messages[code]
    if (error.response?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    if (error.response?.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'
  }
  return 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.'
}
