import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { listProducts } from './api/products'
import { confirmPasswordReset, getMe, login, registerAccount, requestPasswordReset,
  resendVerification, verifyMfa } from './api/auth'
import { changePassword, createAddress, getAccountLimits, getProfile, getSecurity,
  confirmMfa, confirmTotp, disableMfa, listAddresses, listSessions, revokeSession, startMfa,
  startMfaStepUp, startTotp, updateProfile } from './api/profile'
import { getUserDetail, inviteStaff, listAudit, listInvitations, listSettings, listUsers,
  resendInvitation, revokeUserSessions } from './api/admin'

vi.mock('./api/products', () => ({ listProducts: vi.fn() }))
vi.mock('./api/auth', () => ({
  login: vi.fn(), registerAccount: vi.fn(), getMe: vi.fn(), logout: vi.fn(), verifyAccount: vi.fn(),
  requestPasswordReset: vi.fn(), confirmPasswordReset: vi.fn(), resendVerification: vi.fn(), verifyMfa: vi.fn(),
}))
vi.mock('./api/profile', () => ({ getProfile: vi.fn(), updateProfile: vi.fn(), listAddresses: vi.fn(),
  createAddress: vi.fn(), updateAddress: vi.fn(), deleteAddress: vi.fn(), getAccountLimits: vi.fn(),
  getSecurity: vi.fn(), changePassword: vi.fn(), listSessions: vi.fn(), revokeSession: vi.fn(),
  revokeOtherSessions: vi.fn(), startMfa: vi.fn(), confirmMfa: vi.fn(), disableMfa: vi.fn(),
  regenerateRecoveryCodes: vi.fn(), startMfaStepUp: vi.fn(), startTotp: vi.fn(), confirmTotp: vi.fn() }))
vi.mock('./api/admin', () => ({ listUsers: vi.fn(), updateUser: vi.fn(),
  changeRole: vi.fn(), changeStatus: vi.fn(), listSettings: vi.fn(), changeSetting: vi.fn(), listAudit: vi.fn(),
  listInvitations: vi.fn(), inviteStaff: vi.fn(), resendInvitation: vi.fn(), revokeInvitation: vi.fn(),
  getUserDetail: vi.fn(), revokeUserSessions: vi.fn(), acceptInvitation: vi.fn() }))

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

describe('App', () => {
  beforeEach(() => { vi.resetAllMocks()
    vi.mocked(getMe).mockRejectedValue(new Error('Không có phiên'))
    vi.mocked(getAccountLimits).mockResolvedValue({ maxSavedAddresses: 10 }) })
  afterEach(() => cleanup())

  it('renders products returned by the API client', async () => {
    vi.mocked(listProducts).mockResolvedValue({
      items: [{ id: 1, sku: 'TEST-1', name: 'Sample product', description: null,
        imageUrl: null, price: 120000, categoryName: 'Test category', availableQuantity: 5 }],
      page: 0, size: 20, totalItems: 1, totalPages: 1,
    })
    renderAt('/')
    expect(await screen.findByText('Sample product')).toBeInTheDocument()
  })

  it('switches from sign in to registration', () => {
    renderAt('/auth')
    fireEvent.click(screen.getByRole('tab', { name: 'ĐĂNG KÝ' }))
    expect(screen.getByRole('heading', { name: 'Tạo tài khoản' })).toBeInTheDocument()
    expect(screen.getByLabelText('HỌ VÀ TÊN')).toBeInTheDocument()
    expect(screen.getByLabelText('NHẬP LẠI MẬT KHẨU')).toBeInTheDocument()
  })

  it('registers then directs the user to verify email', async () => {
    vi.mocked(registerAccount).mockResolvedValue({ userId: 1, verificationRequired: true, emailSent: true })
    renderAt('/auth')
    fireEvent.click(screen.getByRole('tab', { name: 'ĐĂNG KÝ' }))
    fireEvent.change(screen.getByLabelText('HỌ VÀ TÊN'), { target: { value: 'Test User' } })
    fireEvent.change(screen.getByLabelText('ĐỊA CHỈ EMAIL'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('MẬT KHẨU'), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('NHẬP LẠI MẬT KHẨU'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: 'TẠO TÀI KHOẢN' }))
    expect(await screen.findByRole('heading', { name: 'Kiểm tra email' })).toBeInTheDocument()
    expect(registerAccount).toHaveBeenCalledWith({ fullName: 'Test User', email: 'test@example.com',
      password: 'password123', confirmPassword: 'password123' })
    expect(login).not.toHaveBeenCalled()
  })

  it('does not submit registration when passwords differ', () => {
    renderAt('/auth')
    fireEvent.click(screen.getByRole('tab', { name: 'ĐĂNG KÝ' }))
    fireEvent.change(screen.getByLabelText('HỌ VÀ TÊN'), { target: { value: 'Test User' } })
    fireEvent.change(screen.getByLabelText('ĐỊA CHỈ EMAIL'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('MẬT KHẨU'), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('NHẬP LẠI MẬT KHẨU'), { target: { value: 'different123' } })
    fireEvent.click(screen.getByRole('button', { name: 'TẠO TÀI KHOẢN' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Mật khẩu nhập lại không khớp.')
    expect(registerAccount).not.toHaveBeenCalled()
  })

  it('requests a password reset without revealing whether the account exists', async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue(undefined)
    renderAt('/auth')
    fireEvent.click(screen.getByRole('link', { name: 'Quên mật khẩu?' }))
    expect(screen.getByRole('heading', { name: 'Quên mật khẩu' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('ĐỊA CHỈ EMAIL'), { target: { value: 'test@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'GỬI LIÊN KẾT' }))
    expect(await screen.findByText(/Nếu email thuộc một tài khoản/)).toBeInTheDocument()
    expect(requestPasswordReset).toHaveBeenCalledWith('test@example.com')
  })

  it('requires matching passwords before confirming a reset', async () => {
    vi.mocked(confirmPasswordReset).mockResolvedValue({ reset: true })
    renderAt('/reset-password?token=test-token')
    fireEvent.change(screen.getByLabelText('MẬT KHẨU MỚI'), { target: { value: 'NewPassword123' } })
    fireEvent.change(screen.getByLabelText('NHẬP LẠI MẬT KHẨU MỚI'), { target: { value: 'Different123' } })
    fireEvent.click(screen.getByRole('button', { name: 'ĐẶT LẠI MẬT KHẨU' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Mật khẩu nhập lại không khớp.')
    expect(confirmPasswordReset).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('NHẬP LẠI MẬT KHẨU MỚI'), { target: { value: 'NewPassword123' } })
    fireEvent.click(screen.getByRole('button', { name: 'ĐẶT LẠI MẬT KHẨU' }))
    expect(await screen.findByText(/Mật khẩu đã được cập nhật/)).toBeInTheDocument()
    expect(confirmPasswordReset).toHaveBeenCalledWith({ token: 'test-token',
      newPassword: 'NewPassword123', confirmPassword: 'NewPassword123' })
  })

  it('loads and updates the authenticated profile', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 1, email: 'test@example.com', fullName: 'Test User',
      phone: null, role: 'CUSTOMER' })
    vi.mocked(listAddresses).mockResolvedValue([])
    vi.mocked(updateProfile).mockResolvedValue({ id: 1, email: 'test@example.com',
      fullName: 'Updated User', phone: '0901234567', role: 'CUSTOMER' })
    renderAt('/me')
    expect(await screen.findByText('test@example.com')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('HỌ VÀ TÊN'), { target: { value: 'Updated User' } })
    fireEvent.change(screen.getByLabelText('SỐ ĐIỆN THOẠI'), { target: { value: '0901234567' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
    expect(await screen.findByText('Đã cập nhật hồ sơ cá nhân.')).toBeInTheDocument()
    expect(updateProfile).toHaveBeenCalledWith({ fullName: 'Updated User', phone: '0901234567' })
  })

  it('saves a customer address and reloads the list', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 1, email: 'test@example.com', fullName: 'Test User',
      phone: null, role: 'CUSTOMER' })
    vi.mocked(listAddresses).mockResolvedValueOnce([]).mockResolvedValueOnce([{ id: 2,
      recipientName: 'Test User', phone: '0901234567', addressLine: '123 Main Street', ward: null,
      district: null, province: 'Hanoi', isDefault: true }])
    vi.mocked(createAddress).mockResolvedValue({ id: 2, recipientName: 'Test User', phone: '0901234567',
      addressLine: '123 Main Street', ward: null, district: null, province: 'Hanoi', isDefault: true })
    renderAt('/me/addresses')
    expect(await screen.findByText('Chưa có địa chỉ giao hàng')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thêm địa chỉ' }))
    const dialog = screen.getByRole('dialog', { name: 'Thêm địa chỉ mới' })
    fireEvent.change(within(dialog).getByLabelText('HỌ TÊN NGƯỜI NHẬN'), { target: { value: 'Test User' } })
    fireEvent.change(within(dialog).getByLabelText('SỐ ĐIỆN THOẠI'), { target: { value: '0901234567' } })
    fireEvent.change(within(dialog).getByLabelText('ĐỊA CHỈ CỤ THỂ'), { target: { value: '123 Main Street' } })
    fireEvent.change(within(dialog).getByLabelText('TỈNH / THÀNH PHỐ'), { target: { value: 'Hanoi' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Thêm địa chỉ' }))
    expect(await screen.findByText('Đã lưu địa chỉ giao hàng.')).toBeInTheDocument()
    expect(createAddress).toHaveBeenCalledWith({ recipientName: 'Test User', phone: '0901234567',
      addressLine: '123 Main Street', ward: '', district: '', province: 'Hanoi', isDefault: false })
    expect(screen.getByRole('table')).toHaveTextContent('Test User')
    expect(screen.getByText('Mặc định')).toBeInTheDocument()
  })

  it('hides admin controls from a customer', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 1, email: 'customer@example.com',
      fullName: 'Khách hàng', phone: null, role: 'CUSTOMER' })
    renderAt('/internal')
    expect(await screen.findByText('Không có quyền truy cập')).toBeInTheDocument()
    expect(listUsers).not.toHaveBeenCalled()
  })

  it('opens a static dashboard and navigates through the internal sidebar', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 2, email: 'admin@example.com',
      fullName: 'Quản trị viên', phone: null, role: 'ADMIN' })
    vi.mocked(listUsers).mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })
    vi.mocked(listSettings).mockResolvedValue([])
    vi.mocked(listAudit).mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })
    renderAt('/internal')
    expect(await screen.findByRole('heading', { name: 'Tổng quan' })).toBeInTheDocument()
    expect(screen.getByText('BẢN XEM TRƯỚC')).toBeInTheDocument()
    expect(listUsers).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('navigation', { name: 'Điều hướng nội bộ' }))
      .getByRole('button', { name: 'Tài khoản' }))
    expect(await screen.findByText('Danh sách tài khoản')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thiết lập' }))
    expect(await screen.findByRole('heading', { name: 'Thiết lập hệ thống', level: 1 })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Nhật ký' }))
    expect(await screen.findByRole('heading', { name: 'Nhật ký thao tác', level: 1 })).toBeInTheDocument()
  })

  it('lets staff open the internal workspace without showing Admin-only tools', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 3, email: 'warehouse@example.com',
      fullName: 'Nhân viên kho', phone: null, role: 'WAREHOUSE' })
    renderAt('/internal')
    expect(await screen.findByRole('heading', { name: 'Tổng quan' })).toBeInTheDocument()
    expect(screen.getAllByText('Nhân viên kho').length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Tài khoản' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Thiết lập' })).not.toBeInTheDocument()
    expect(listUsers).not.toHaveBeenCalled()
  })

  it('sends internal staff to the internal workspace after login', async () => {
    vi.mocked(login).mockResolvedValue({ mfaRequired: false, challengeToken: null,
      user: { id: 4, email: 'support@example.com', fullName: 'Nhân viên hỗ trợ', role: 'SUPPORT' } })
    vi.mocked(getProfile).mockResolvedValue({ id: 4, email: 'support@example.com',
      fullName: 'Nhân viên hỗ trợ', phone: null, role: 'SUPPORT' })
    renderAt('/auth')
    fireEvent.change(screen.getByLabelText('ĐỊA CHỈ EMAIL'), { target: { value: 'support@example.com' } })
    fireEvent.change(screen.getByLabelText('MẬT KHẨU'), { target: { value: 'Password123' } })
    fireEvent.click(screen.getByRole('button', { name: 'ĐĂNG NHẬP' }))
    expect(await screen.findByRole('heading', { name: 'Tổng quan' })).toBeInTheDocument()
  })

  it('offers a resend action on the registration confirmation screen', async () => {
    vi.mocked(resendVerification).mockResolvedValue(undefined)
    renderAt('/registration-sent?email=customer@example.com')
    expect(screen.getByRole('heading', { name: 'Kiểm tra email' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'GỬI LẠI EMAIL XÁC MINH' }))
    expect(await screen.findByText(/email sẽ được gửi theo giới hạn/)).toBeInTheDocument()
    expect(resendVerification).toHaveBeenCalledWith('customer@example.com')
  })

  it('requires the second factor before opening the internal workspace', async () => {
    vi.mocked(login).mockResolvedValue({ user: null, mfaRequired: true, challengeToken: 'challenge-1' })
    vi.mocked(verifyMfa).mockResolvedValue({ user: { id: 7, email: 'admin@example.com',
      fullName: 'Admin', role: 'ADMIN' }, mfaRequired: false, challengeToken: null })
    vi.mocked(getProfile).mockResolvedValue({ id: 7, email: 'admin@example.com',
      fullName: 'Admin', phone: null, role: 'ADMIN' })
    renderAt('/auth')
    fireEvent.change(screen.getByLabelText('ĐỊA CHỈ EMAIL'), { target: { value: 'admin@example.com' } })
    fireEvent.change(screen.getByLabelText('MẬT KHẨU'), { target: { value: 'StrongPass123' } })
    fireEvent.click(screen.getByRole('button', { name: 'ĐĂNG NHẬP' }))
    expect(await screen.findByRole('heading', { name: 'Xác minh đăng nhập' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('MÃ XÁC MINH HOẶC MÃ KHÔI PHỤC'),
      { target: { value: '123456' } })
    fireEvent.click(screen.getByRole('button', { name: 'XÁC MINH' }))
    expect(await screen.findByRole('heading', { name: 'Tổng quan' })).toBeInTheDocument()
    expect(verifyMfa).toHaveBeenCalledWith('challenge-1', '123456')
  })

  it('lets a signed-in user change password and returns to login', async () => {
    vi.mocked(getSecurity).mockResolvedValue({ mfaEnabled: false, mfaMethod: 'EMAIL', recoveryCodesRemaining: 0 })
    vi.mocked(changePassword).mockResolvedValue(undefined)
    renderAt('/me/security')
    fireEvent.change(screen.getByLabelText('MẬT KHẨU HIỆN TẠI'), { target: { value: 'OldPassword123' } })
    fireEvent.change(screen.getByLabelText('MẬT KHẨU MỚI'), { target: { value: 'NewPassword123' } })
    fireEvent.change(screen.getByLabelText('NHẬP LẠI MẬT KHẨU'), { target: { value: 'NewPassword123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật mật khẩu' }))
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
    expect(changePassword).toHaveBeenCalledWith({ currentPassword: 'OldPassword123',
      newPassword: 'NewPassword123', confirmPassword: 'NewPassword123' })
  })

  it('shows one-time recovery codes after enabling email verification', async () => {
    vi.mocked(getSecurity).mockResolvedValue({ mfaEnabled: false, mfaMethod: 'EMAIL', recoveryCodesRemaining: 0 })
    vi.mocked(startMfa).mockResolvedValue({ challengeToken: 'enroll-1' })
    vi.mocked(confirmMfa).mockResolvedValue({ recoveryCodes: ['ABCD1234-ABCD1234-ABCD1234-ABCD1234'] })
    renderAt('/me/security')
    fireEvent.click(await screen.findByRole('button', { name: 'Bật xác minh hai bước' }))
    fireEvent.change(await screen.findByLabelText('MÃ SÁU CHỮ SỐ'), { target: { value: '123456' } })
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận và bật' }))
    expect(await screen.findByText('Mã khôi phục — chỉ hiển thị lần này')).toBeInTheDocument()
    expect(screen.getByText('ABCD1234-ABCD1234-ABCD1234-ABCD1234')).toBeInTheDocument()
  })

  it('lets a user set up an authenticator app and shows the secret once', async () => {
    vi.mocked(getSecurity).mockResolvedValue({ mfaEnabled: false, mfaMethod: 'EMAIL', recoveryCodesRemaining: 0 })
    vi.mocked(startTotp).mockResolvedValue({ secret: 'JBSWY3DPEHPK3PXP',
      otpauthUri: 'otpauth://totp/FORME:test?secret=JBSWY3DPEHPK3PXP' })
    vi.mocked(confirmTotp).mockResolvedValue({ recoveryCodes: ['ABCD1234-ABCD1234-ABCD1234-ABCD1234'] })
    renderAt('/me/security')
    fireEvent.change(await screen.findByLabelText('MẬT KHẨU ĐỂ BẬT ỨNG DỤNG'),
      { target: { value: 'StrongPass123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Thiết lập ứng dụng xác thực' }))
    expect(await screen.findByText('JBSWY3DPEHPK3PXP')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('MÃ TỪ ỨNG DỤNG'), { target: { value: '123456' } })
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận ứng dụng xác thực' }))
    expect(await screen.findByText('Mã khôi phục — chỉ hiển thị lần này')).toBeInTheDocument()
    expect(confirmTotp).toHaveBeenCalledWith('123456')
  })

  it('requires the existing MFA factor before disabling it', async () => {
    vi.mocked(getSecurity).mockResolvedValue({ mfaEnabled: true, mfaMethod: 'EMAIL', recoveryCodesRemaining: 8 })
    vi.mocked(startMfaStepUp).mockResolvedValue({ challengeToken: 'step-up-1', mfaMethod: 'EMAIL' })
    vi.mocked(disableMfa).mockResolvedValue(undefined)
    renderAt('/me/security')
    fireEvent.change(await screen.findByLabelText('MẬT KHẨU ĐỂ TẮT'),
      { target: { value: 'StrongPass123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tắt xác minh hai bước' }))
    expect(await screen.findByLabelText('MÃ MFA HIỆN TẠI HOẶC MÃ KHÔI PHỤC')).toBeInTheDocument()
    expect(disableMfa).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('MÃ MFA HIỆN TẠI HOẶC MÃ KHÔI PHỤC'),
      { target: { value: '123456' } })
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận tắt MFA' }))
    expect(await screen.findByText('Đã tắt xác minh hai bước.')).toBeInTheDocument()
    expect(disableMfa).toHaveBeenCalledWith('StrongPass123', 'step-up-1', '123456')
  })

  it('lists active sessions and revokes one other device', async () => {
    vi.mocked(listSessions).mockResolvedValue([{ id: 1, ipAddress: '127.0.0.1', userAgent: 'Thiết bị này',
      createdAt: '2026-10-10T00:00:00Z', lastSeenAt: '2026-10-10T01:00:00Z',
      expiresAt: '2026-10-11T00:00:00Z', current: true },
      { id: 2, ipAddress: '192.168.1.4', userAgent: 'Thiết bị khác',
        createdAt: '2026-10-10T00:00:00Z', lastSeenAt: null,
        expiresAt: '2026-10-11T00:00:00Z', current: false }])
    vi.mocked(revokeSession).mockResolvedValue(undefined)
    renderAt('/me/sessions')
    expect(await screen.findByText('Thiết bị khác')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }))
    expect(await screen.findByText('Đã kết thúc phiên đăng nhập.')).toBeInTheDocument()
    expect(revokeSession).toHaveBeenCalledWith(2)
  })

  it('lets Admin invite staff without entering their password', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 9, email: 'admin@example.com',
      fullName: 'Admin', phone: null, role: 'ADMIN' })
    vi.mocked(listInvitations).mockResolvedValue([])
    vi.mocked(inviteStaff).mockResolvedValue({ id: 1, email: 'staff@example.com', fullName: 'Staff',
      role: 'WAREHOUSE', createdAt: '2026-10-10T00:00:00Z', expiresAt: '2026-10-12T00:00:00Z',
      acceptedAt: null, status: 'SENT' })
    renderAt('/internal/invitations')
    expect(await screen.findByRole('heading', { name: 'Lời mời nhân viên', level: 1 })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '＋ Gửi lời mời' }))
    const dialog = screen.getByRole('dialog', { name: 'Gửi lời mời' })
    expect(within(dialog).queryByLabelText(/mật khẩu/i)).not.toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText('HỌ VÀ TÊN'), { target: { value: 'Staff' } })
    fireEvent.change(within(dialog).getByLabelText('ĐỊA CHỈ EMAIL'), { target: { value: 'staff@example.com' } })
    fireEvent.change(within(dialog).getByLabelText('VAI TRÒ'), { target: { value: 'WAREHOUSE' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Gửi lời mời' }))
    expect(await screen.findByText(/Đã gửi lời mời qua email/)).toBeInTheDocument()
    expect(inviteStaff).toHaveBeenCalledWith({ email: 'staff@example.com', fullName: 'Staff',
      phone: '', role: 'WAREHOUSE' })
  })

  it('lets Admin resend a failed invitation', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 9, email: 'admin@example.com',
      fullName: 'Admin', phone: null, role: 'ADMIN' })
    vi.mocked(listInvitations).mockResolvedValue([{ id: 4, email: 'staff@example.com', fullName: 'Staff',
      role: 'SUPPORT', createdAt: '2026-10-10T00:00:00Z', expiresAt: '2026-10-12T00:00:00Z',
      acceptedAt: null, status: 'FAILED' }])
    vi.mocked(resendInvitation).mockResolvedValue({ id: 5, email: 'staff@example.com', fullName: 'Staff',
      role: 'SUPPORT', createdAt: '2026-10-10T00:00:00Z', expiresAt: '2026-10-12T00:00:00Z',
      acceptedAt: null, status: 'SENT' })
    renderAt('/internal/invitations')
    fireEvent.click(await screen.findByRole('button', { name: 'Gửi lại' }))
    expect(await screen.findByText('Đã gửi lời mời mới.')).toBeInTheDocument()
    expect(resendInvitation).toHaveBeenCalledWith(4)
  })

  it('shows Admin user detail with sessions and history', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 9, email: 'admin@example.com',
      fullName: 'Admin', phone: null, role: 'ADMIN' })
    vi.mocked(getUserDetail).mockResolvedValue({ user: { id: 3, email: 'staff@example.com',
      fullName: 'Nhân viên A', phone: null, role: 'SUPPORT', status: 'ACTIVE',
      createdAt: '2026-10-10T00:00:00Z', verifiedAt: '2026-10-10T00:00:00Z' },
    sessions: [{ id: 8, ipAddress: '127.0.0.1', userAgent: 'Chrome',
      createdAt: '2026-10-10T00:00:00Z', lastSeenAt: '2026-10-10T01:00:00Z',
      expiresAt: '2026-10-11T00:00:00Z' }],
    history: [{ id: 2, actorId: 9, actorEmail: 'admin@example.com', action: 'USER_ROLE_CHANGED',
      entityType: 'USER', entityId: '3', createdAt: '2026-10-10T00:00:00Z' }] })
    vi.mocked(revokeUserSessions).mockResolvedValue(undefined)
    renderAt('/internal/users/3')
    expect(await screen.findByText('Nhân viên A')).toBeInTheDocument()
    expect(screen.getByText('Chrome')).toBeInTheDocument()
    expect(screen.getByText('Đổi vai trò')).toBeInTheDocument()
    expect(getUserDetail).toHaveBeenCalledWith(3)
  })
})
