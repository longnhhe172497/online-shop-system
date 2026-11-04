import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import ProfilePage from './ProfilePage'
import { createAddress, deleteAddress, getAccountLimits, getProfile, listAddresses,
  updateAddress, updateProfile, type Address, type Profile } from './api/profile'

vi.mock('./api/profile', () => ({ getProfile: vi.fn(), updateProfile: vi.fn(),
  listAddresses: vi.fn(), getAccountLimits: vi.fn(), createAddress: vi.fn(),
  updateAddress: vi.fn(), deleteAddress: vi.fn() }))
vi.mock('./SignOutButton', () => ({ default: () => null }))

const customer: Profile = { id: 1, email: 'customer@example.com', fullName: 'Khách Hàng',
  phone: null, role: 'CUSTOMER' }
const address: Address = { id: 7, recipientName: 'Người nhận', phone: '0901234567',
  addressLine: '123 Đường A', ward: 'Phường 1', district: 'Quận 1', province: 'Hà Nội',
  isDefault: true }

beforeEach(() => { vi.resetAllMocks(); vi.mocked(getProfile).mockResolvedValue(customer)
  vi.mocked(getAccountLimits).mockResolvedValue({ maxSavedAddresses: 10 })
  vi.mocked(listAddresses).mockResolvedValue([address]) })
afterEach(cleanup)

function openPage(path: string) { render(<MemoryRouter initialEntries={[path]}><ProfilePage /></MemoryRouter>) }

it('updates profile fields while keeping the login email read-only', async () => {
  vi.mocked(updateProfile).mockResolvedValue({ ...customer, fullName: 'Tên mới', phone: '0909000000' })
  openPage('/me')
  expect(await screen.findByDisplayValue(customer.email)).toBeDisabled()
  fireEvent.change(screen.getByLabelText('HỌ VÀ TÊN'), { target: { value: 'Tên mới' } })
  fireEvent.change(screen.getByLabelText('SỐ ĐIỆN THOẠI'), { target: { value: '0909000000' } })
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
  await waitFor(() => expect(updateProfile).toHaveBeenCalledWith({ fullName: 'Tên mới', phone: '0909000000' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Đã cập nhật hồ sơ cá nhân.')
})

it('rejects special characters in the name and requires a ten-digit phone number', async () => {
  openPage('/me')
  await screen.findByDisplayValue(customer.email)

  fireEvent.change(screen.getByLabelText('HỌ VÀ TÊN'), { target: { value: 'Long 123@@' } })
  fireEvent.change(screen.getByLabelText('SỐ ĐIỆN THOẠI'), { target: { value: '0348abc@@' } })

  expect(screen.getByLabelText(/^SỐ ĐIỆN THOẠI/)).toHaveValue('0348')
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

  expect(screen.getByText('Họ và tên chỉ được chứa chữ cái và khoảng trắng.')).toBeInTheDocument()
  expect(screen.getByText('Số điện thoại phải gồm đúng 10 chữ số.')).toBeInTheDocument()
  expect(updateProfile).not.toHaveBeenCalled()
})

it('adds and edits an address in the desktop address dialog', async () => {
  vi.mocked(createAddress).mockResolvedValue(address)
  vi.mocked(updateAddress).mockResolvedValue({ ...address, recipientName: 'Người nhận mới' })
  openPage('/me/addresses')
  expect(await screen.findByRole('table')).toHaveTextContent('123 Đường A')
  fireEvent.click(screen.getByRole('button', { name: /Thêm địa chỉ/ }))
  const addDialog = screen.getByRole('dialog', { name: 'Thêm địa chỉ mới' })
  fireEvent.change(within(addDialog).getByLabelText('HỌ TÊN NGƯỜI NHẬN'),
    { target: { value: 'Người nhận mới' } })
  fireEvent.change(within(addDialog).getByLabelText('SỐ ĐIỆN THOẠI'),
    { target: { value: '0909999999' } })
  fireEvent.change(within(addDialog).getByLabelText('ĐỊA CHỈ CỤ THỂ'),
    { target: { value: '456 Đường B' } })
  fireEvent.change(within(addDialog).getByLabelText('TỈNH / THÀNH PHỐ'),
    { target: { value: 'Hồ Chí Minh' } })
  fireEvent.click(within(addDialog).getByRole('button', { name: 'Thêm địa chỉ' }))
  await waitFor(() => expect(createAddress).toHaveBeenCalledWith(expect.objectContaining({
    recipientName: 'Người nhận mới', phone: '0909999999', addressLine: '456 Đường B',
    province: 'Hồ Chí Minh' })))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Chỉnh sửa' }))
  const editDialog = screen.getByRole('dialog', { name: 'Chỉnh sửa địa chỉ' })
  expect(within(editDialog).getByLabelText('HỌ TÊN NGƯỜI NHẬN')).toHaveValue('Người nhận')
  expect(within(editDialog).getByRole('checkbox', { name: /Đặt làm địa chỉ mặc định/ })).toBeDisabled()
  fireEvent.change(within(editDialog).getByLabelText('HỌ TÊN NGƯỜI NHẬN'),
    { target: { value: 'Người nhận mới' } })
  fireEvent.click(within(editDialog).getByRole('button', { name: 'Lưu thay đổi' }))
  await waitFor(() => expect(updateAddress).toHaveBeenCalledWith(7,
    expect.objectContaining({ recipientName: 'Người nhận mới', isDefault: true })))
})

it('rejects invalid recipient names and phone numbers before creating an address', async () => {
  openPage('/me/addresses')
  await screen.findByRole('table')
  fireEvent.click(screen.getByRole('button', { name: /Thêm địa chỉ/ }))
  const dialog = screen.getByRole('dialog', { name: 'Thêm địa chỉ mới' })
  fireEvent.change(within(dialog).getByLabelText('HỌ TÊN NGƯỜI NHẬN'),
    { target: { value: 'Long 123@@' } })
  fireEvent.change(within(dialog).getByLabelText('SỐ ĐIỆN THOẠI'),
    { target: { value: '0901abc@@' } })
  fireEvent.submit(within(dialog).getByRole('button', { name: 'Thêm địa chỉ' }).closest('form')!)

  expect(within(dialog).getByText('Họ tên người nhận chỉ được chứa chữ cái và khoảng trắng.'))
    .toBeInTheDocument()
  expect(within(dialog).getByText('Số điện thoại phải gồm đúng 10 chữ số.')).toBeInTheDocument()
  expect(within(dialog).getByLabelText(/^SỐ ĐIỆN THOẠI/)).toHaveValue('0901')
  expect(createAddress).not.toHaveBeenCalled()
})

it('shows Vietnamese validation for missing required address fields', async () => {
  openPage('/me/addresses')
  await screen.findByRole('table')
  fireEvent.click(screen.getByRole('button', { name: /thêm địa chỉ/i }))
  const dialog = screen.getByRole('dialog')

  fireEvent.change(within(dialog).getByLabelText('HỌ TÊN NGƯỜI NHẬN'),
    { target: { value: 'Nguyễn Văn An' } })
  fireEvent.change(within(dialog).getByLabelText('SỐ ĐIỆN THOẠI'),
    { target: { value: '0901234567' } })
  fireEvent.submit(within(dialog).getByRole('button', { name: 'Thêm địa chỉ' }).closest('form')!)

  expect(await within(dialog).findByText('Địa chỉ cụ thể không được để trống.')).toBeInTheDocument()
  expect(within(dialog).getByText('Tỉnh / Thành phố không được để trống.')).toBeInTheDocument()
  expect(createAddress).not.toHaveBeenCalled()
})

it('asks for confirmation before deleting and hides the address editor from staff', async () => {
  vi.mocked(deleteAddress).mockResolvedValue()
  vi.spyOn(window, 'confirm').mockReturnValue(false)
  openPage('/me/addresses')
  fireEvent.click(await screen.findByRole('button', { name: 'Xóa' }))
  expect(deleteAddress).not.toHaveBeenCalled()
  vi.mocked(window.confirm).mockReturnValue(true)
  fireEvent.click(screen.getByRole('button', { name: 'Xóa' }))
  await waitFor(() => expect(deleteAddress).toHaveBeenCalledWith(7))
  vi.mocked(window.confirm).mockRestore()
  cleanup()
  vi.mocked(getProfile).mockResolvedValue({ ...customer, role: 'SUPPORT' })
  openPage('/me/addresses')
  expect(await screen.findByText('Chỉ tài khoản khách hàng sử dụng sổ địa chỉ giao hàng.'))
    .toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Thêm địa chỉ/ })).not.toBeInTheDocument()
})
