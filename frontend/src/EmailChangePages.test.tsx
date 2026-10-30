import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { EmailChangeConfirmPage, EmailChangePage } from './EmailChangePages'
import { confirmEmailChange, getProfile, requestEmailChange } from './api/profile'

vi.mock('./api/profile', () => ({ getProfile: vi.fn(), requestEmailChange: vi.fn(),
  confirmEmailChange: vi.fn() }))
vi.mock('./SignOutButton', () => ({ default: () => null }))

beforeEach(() => { vi.resetAllMocks(); vi.mocked(getProfile).mockResolvedValue({ id: 1,
  email: 'old@example.com', fullName: 'Khách Hàng', phone: null, role: 'CUSTOMER' }) })
afterEach(cleanup)

it('requests email change with the current password and explains session revocation', async () => {
  vi.mocked(requestEmailChange).mockResolvedValue()
  render(<MemoryRouter><EmailChangePage /></MemoryRouter>)
  expect(await screen.findByText('old@example.com')).toBeInTheDocument()
  expect(screen.getByText(/mọi thiết bị sẽ đăng xuất/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('EMAIL MỚI'), { target: { value: 'new@example.com' } })
  fireEvent.change(screen.getByLabelText('MẬT KHẨU HIỆN TẠI'),
    { target: { value: 'Password123' } })
  fireEvent.click(screen.getByRole('button', { name: 'Gửi liên kết xác nhận' }))
  await waitFor(() => expect(requestEmailChange).toHaveBeenCalledWith('new@example.com', 'Password123'))
  expect(await screen.findByRole('status')).toHaveTextContent('new@example.com')
  expect(screen.queryByLabelText('MẬT KHẨU HIỆN TẠI')).not.toBeInTheDocument()
})

it('shows a failed request without claiming the email was sent', async () => {
  vi.mocked(requestEmailChange).mockRejectedValue({ isAxiosError: true,
    response: { status: 400, data: { code: 'INVALID_CURRENT_PASSWORD' } } })
  render(<MemoryRouter><EmailChangePage /></MemoryRouter>)
  fireEvent.change(await screen.findByLabelText('EMAIL MỚI'), { target: { value: 'new@example.com' } })
  fireEvent.change(screen.getByLabelText('MẬT KHẨU HIỆN TẠI'),
    { target: { value: 'WrongPassword' } })
  fireEvent.click(screen.getByRole('button', { name: 'Gửi liên kết xác nhận' }))
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(screen.queryByText(/Đã gửi liên kết tới/)).not.toBeInTheDocument()
})

it('requires a token and confirms a valid link only after user action', async () => {
  render(<MemoryRouter initialEntries={['/email-change/confirm']}><EmailChangeConfirmPage /></MemoryRouter>)
  expect(screen.getByText('Liên kết không có mã xác nhận.')).toBeInTheDocument()
  expect(confirmEmailChange).not.toHaveBeenCalled()
  cleanup()
  vi.mocked(confirmEmailChange).mockResolvedValue()
  render(<MemoryRouter initialEntries={['/email-change/confirm?token=valid-token']}>
    <EmailChangeConfirmPage /></MemoryRouter>)
  expect(confirmEmailChange).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'XÁC NHẬN EMAIL MỚI' }))
  await waitFor(() => expect(confirmEmailChange).toHaveBeenCalledWith('valid-token'))
  expect(await screen.findByRole('status')).toHaveTextContent('Mọi thiết bị đã đăng xuất')
  expect(screen.getByRole('link', { name: /Đăng nhập bằng email mới/ })).toHaveAttribute('href', '/auth')
})
