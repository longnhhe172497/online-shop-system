import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import AccountSecurityPage from './AccountSecurityPage'
import { getSecurity, startMfa, startTotp } from './api/profile'
import { getGoogleLinkStatus, linkGoogle } from './api/auth'

vi.mock('./api/profile', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api/profile')>()),
  getSecurity: vi.fn(), startMfa: vi.fn(), startTotp: vi.fn(),
}))
vi.mock('./api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api/auth')>()),
  getGoogleLinkStatus: vi.fn(), linkGoogle: vi.fn(),
}))
vi.mock('./GoogleButton', () => ({ default: ({ onCredential, disabled }: {
  onCredential: (credential: string) => void; disabled: boolean
}) => <button type="button" disabled={disabled}
  onClick={() => onCredential('other-google-id-token')}>Đăng nhập bằng Google</button> }))

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getSecurity).mockResolvedValue({ mfaEnabled: false, mfaMethod: 'EMAIL', recoveryCodesRemaining: 0 })
  vi.mocked(getGoogleLinkStatus).mockResolvedValue({ linked: false })
})
afterEach(cleanup)

it('shows the wrong-Google-account error inside the linking card', async () => {
  vi.mocked(linkGoogle).mockRejectedValue({ isAxiosError: true,
    response: { status: 409, data: { code: 'GOOGLE_EMAIL_MISMATCH' } } })
  render(<MemoryRouter><AccountSecurityPage /></MemoryRouter>)
  fireEvent.change(screen.getByLabelText('MẬT KHẨU ĐỂ LIÊN KẾT GOOGLE'),
    { target: { value: 'CurrentPassword123' } })
  const googlePanel = screen.getByRole('heading', { name: 'Đăng nhập bằng Google' }).closest('section')!
  fireEvent.click(within(googlePanel).getByRole('button', { name: 'Đăng nhập bằng Google' }))
  await waitFor(() => expect(linkGoogle).toHaveBeenCalledWith('CurrentPassword123', 'other-google-id-token'))
  expect(await within(googlePanel).findByRole('alert')).toHaveTextContent(
    'Bạn đã chọn tài khoản Google khác email đang đăng nhập. Hãy chọn đúng tài khoản rồi thử lại.')
  expect(screen.getAllByRole('alert')).toHaveLength(1)
})

it('hides email setup while authenticator enrollment awaits its code', async () => {
  vi.mocked(startTotp).mockResolvedValue({ secret: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://test' })
  render(<MemoryRouter><AccountSecurityPage /></MemoryRouter>)
  fireEvent.change(screen.getByLabelText('MẬT KHẨU ĐỂ BẬT ỨNG DỤNG'),
    { target: { value: 'CurrentPassword123' } })
  fireEvent.click(await screen.findByRole('button', { name: 'Thiết lập ứng dụng xác thực' }))
  expect(await screen.findByText('Đang thiết lập ứng dụng')).toBeInTheDocument()
  expect(screen.getByLabelText('MÃ 6 SỐ TỪ ỨNG DỤNG')).toBeInTheDocument()
  expect(screen.getByText(/Khóa này chỉ hiển thị trong lần thiết lập, không phải mã để đăng nhập/))
    .toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Bật mã qua email' })).not.toBeInTheDocument()
})

it('hides authenticator setup while email enrollment awaits its code', async () => {
  vi.mocked(startMfa).mockResolvedValue({ challengeToken: 'email-enroll-token' })
  render(<MemoryRouter><AccountSecurityPage /></MemoryRouter>)
  fireEvent.click(await screen.findByRole('button', { name: 'Bật mã qua email' }))
  expect(await screen.findByText('Đang xác nhận email')).toBeInTheDocument()
  expect(screen.getByLabelText('MÃ SÁU CHỮ SỐ')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Thiết lập ứng dụng xác thực' })).not.toBeInTheDocument()
})
