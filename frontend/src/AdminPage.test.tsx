import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'
import { getProfile } from './api/profile'
import { changeRole, changeSetting, listAudit, listInvitations, listSettings, listUsers,
  revokeInvitation, type Invitation, type ManagedUser, type Setting } from './api/admin'

vi.mock('./api/profile', () => ({ getProfile: vi.fn() }))
vi.mock('./api/admin', () => ({ listUsers: vi.fn(), getUserDetail: vi.fn(), updateUser: vi.fn(),
  changeRole: vi.fn(), changeStatus: vi.fn(), listSettings: vi.fn(), changeSetting: vi.fn(),
  listAudit: vi.fn(), listInvitations: vi.fn(), inviteStaff: vi.fn(), resendInvitation: vi.fn(),
  revokeInvitation: vi.fn(), revokeUserSessions: vi.fn() }))
vi.mock('./RolePermissionsPage', () => ({ default: () => <div>Ma trận quyền</div> }))
vi.mock('./SignOutButton', () => ({ default: () => null }))

const user: ManagedUser = { id: 4, email: 'staff@example.com', fullName: 'Nhân viên', phone: null,
  role: 'SUPPORT', status: 'ACTIVE', createdAt: '2026-10-01T10:00:00Z',
  verifiedAt: '2026-10-01T10:00:00Z' }
const setting: Setting = { key: 'max_saved_addresses', value: '10',
  description: 'Giới hạn địa chỉ', updatedAt: '2026-10-01T10:00:00Z' }
const invitation: Invitation = { id: 9, email: 'invite@example.com', fullName: 'Người được mời',
  role: 'SUPPORT', expiresAt: '2026-10-20T10:00:00Z', acceptedAt: null,
  createdAt: '2026-10-16T10:00:00Z', status: 'SENT' }

beforeEach(() => { vi.resetAllMocks()
  vi.mocked(getProfile).mockResolvedValue({ id: 1, email: 'admin@example.com', fullName: 'Admin',
    phone: null, role: 'ADMIN' })
  vi.mocked(listUsers).mockResolvedValue({ items: [user], page: 0, size: 20,
    totalItems: 1, totalPages: 1 })
  vi.mocked(listSettings).mockResolvedValue([setting])
  vi.mocked(listInvitations).mockResolvedValue([invitation])
  vi.mocked(listAudit).mockResolvedValue({ items: [], page: 0, size: 20,
    totalItems: 0, totalPages: 0 }) })
afterEach(cleanup)
function openPage(path: string) { render(<MemoryRouter initialEntries={[path]}><AdminPage /></MemoryRouter>) }

it('keeps non-admin staff on the shared dashboard without loading Admin tools', async () => {
  vi.mocked(getProfile).mockResolvedValue({ id: 2, email: 'staff@example.com', fullName: 'Staff',
    phone: null, role: 'SUPPORT' })
  openPage('/support/settings')
  expect(await screen.findByText('Chào mừng đến với khu nội bộ')).toBeInTheDocument()
  const navigation = screen.getByRole('navigation', { name: 'Điều hướng nội bộ' })
  expect(within(navigation).queryByRole('button', { name: 'Tài khoản' })).not.toBeInTheDocument()
  expect(listSettings).not.toHaveBeenCalled()
})

it('requires confirmation before changing a staff role', async () => {
  vi.mocked(changeRole).mockResolvedValue({ ...user, role: 'WAREHOUSE' })
  openPage('/admin/users')
  fireEvent.change(await screen.findByRole('combobox', { name: 'Vai trò của Nhân viên' }),
    { target: { value: 'WAREHOUSE' } })
  const dialog = screen.getByRole('dialog', { name: 'Cập nhật quyền truy cập?' })
  expect(within(dialog).getByText(/Các phiên hiện tại/)).toBeInTheDocument()
  fireEvent.click(within(dialog).getByRole('button', { name: 'Hủy' }))
  expect(changeRole).not.toHaveBeenCalled()
  fireEvent.change(screen.getByRole('combobox', { name: 'Vai trò của Nhân viên' }),
    { target: { value: 'WAREHOUSE' } })
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }))
  await waitFor(() => expect(changeRole).toHaveBeenCalledWith(4, 'WAREHOUSE'))
  expect(await screen.findByRole('status')).toHaveTextContent('Đã cập nhật quyền truy cập.')
})

it('saves a changed setting but not an unchanged value', async () => {
  vi.mocked(changeSetting).mockResolvedValue({ ...setting, value: '12' })
  openPage('/admin/settings')
  const input = await screen.findByLabelText('Số địa chỉ lưu tối đa')
  expect(screen.getByRole('button', { name: 'Lưu' })).toBeDisabled()
  fireEvent.change(input, { target: { value: '12' } })
  fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(changeSetting).toHaveBeenCalledWith('max_saved_addresses', '12'))
  expect(await screen.findByRole('status')).toHaveTextContent('Đã cập nhật thiết lập.')
})

it('applies audit filters only when the filter form is submitted', async () => {
  openPage('/admin/audit')
  await screen.findByText('Chưa có nhật ký.')
  fireEvent.change(screen.getByRole('textbox', { name: 'Lọc theo email người thực hiện' }),
    { target: { value: 'admin@example.com' } })
  expect(listAudit).toHaveBeenCalledTimes(1)
  fireEvent.click(screen.getByRole('button', { name: 'Lọc' }))
  await waitFor(() => expect(listAudit).toHaveBeenCalledWith(0,
    expect.objectContaining({ actor: 'admin@example.com' })))
})

it('requires confirmation before revoking an invitation', async () => {
  vi.mocked(revokeInvitation).mockResolvedValue({ ...invitation, status: 'REVOKED' })
  openPage('/admin/invitations')
  fireEvent.click(await screen.findByRole('button', { name: 'Thu hồi' }))
  expect(revokeInvitation).not.toHaveBeenCalled()
  const dialog = screen.getByRole('dialog', { name: 'Thu hồi lời mời?' })
  expect(within(dialog).getByText('invite@example.com')).toBeInTheDocument()
  fireEvent.click(within(dialog).getByRole('button', { name: 'Thu hồi' }))
  await waitFor(() => expect(revokeInvitation).toHaveBeenCalledWith(9))
  expect(await screen.findByRole('status')).toHaveTextContent('Đã thu hồi lời mời.')
})
