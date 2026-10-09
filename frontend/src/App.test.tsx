import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { listProducts } from './api/products'
import { confirmPasswordReset, login, registerAccount, requestPasswordReset } from './api/auth'
import { createAddress, getProfile, listAddresses, updateProfile } from './api/profile'

vi.mock('./api/products', () => ({ listProducts: vi.fn() }))
vi.mock('./api/auth', () => ({
  login: vi.fn(), registerAccount: vi.fn(), getMe: vi.fn(), logout: vi.fn(), verifyAccount: vi.fn(),
  requestPasswordReset: vi.fn(), confirmPasswordReset: vi.fn(),
}))
vi.mock('./api/client', () => ({ hasAccessToken: () => false }))
vi.mock('./api/profile', () => ({ getProfile: vi.fn(), updateProfile: vi.fn(), listAddresses: vi.fn(),
  createAddress: vi.fn(), updateAddress: vi.fn(), deleteAddress: vi.fn() }))

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

describe('App', () => {
  beforeEach(() => vi.resetAllMocks())
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
    vi.mocked(registerAccount).mockResolvedValue({ userId: 1, verificationRequired: true })
    renderAt('/auth')
    fireEvent.click(screen.getByRole('tab', { name: 'ĐĂNG KÝ' }))
    fireEvent.change(screen.getByLabelText('HỌ VÀ TÊN'), { target: { value: 'Test User' } })
    fireEvent.change(screen.getByLabelText('ĐỊA CHỈ EMAIL'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('MẬT KHẨU'), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('NHẬP LẠI MẬT KHẨU'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: 'TẠO TÀI KHOẢN' }))
    expect(await screen.findByText(/Tài khoản đã được tạo/)).toBeInTheDocument()
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
    renderAt('/me')
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
})
