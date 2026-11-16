import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import ProfilePage from './ProfilePage'
import divisions from './reference/vietnam-divisions.json'
import { createAddress, deleteAddress, getAccountLimits, getProfile, listAddresses,
  updateAddress, updateProfile, type Address, type Profile } from './api/profile'

vi.mock('./api/profile', () => ({ getProfile: vi.fn(), updateProfile: vi.fn(),
  listAddresses: vi.fn(), getAccountLimits: vi.fn(), createAddress: vi.fn(),
  updateAddress: vi.fn(), deleteAddress: vi.fn() }))
vi.mock('./SignOutButton', () => ({ default: () => null }))

const customer: Profile = { id: 1, email: 'customer@example.com', fullName: 'Khách Hàng',
  phone: null, role: 'CUSTOMER' }
const address: Address = { id: 7, recipientName: 'Người nhận', phone: '0901234567',
  addressLine: '123 Đường A', ward: 'Phường Ba Đình', district: '', province: 'Hà Nội',
  isDefault: true }

beforeEach(() => { vi.resetAllMocks(); vi.mocked(getProfile).mockResolvedValue(customer)
  vi.mocked(getAccountLimits).mockResolvedValue({ maxSavedAddresses: 10 })
  vi.mocked(listAddresses).mockResolvedValue([address]) })
afterEach(cleanup)

function openPage(path: string) { render(<MemoryRouter initialEntries={[path]}><ProfilePage /></MemoryRouter>) }

it('bundles complete two-level data with unique codes and Hanoi wards', () => {
  expect(divisions).toHaveLength(34)
  const wards = divisions.flatMap((province) => province.wards)
  expect(wards).toHaveLength(3321)
  expect(new Set(wards.map((ward) => ward.code)).size).toBe(3321)
  expect(new Set(divisions.map((province) => province.code)).size).toBe(34)
  expect(divisions.every((province) => province.wards.length > 0)).toBe(true)
  expect(divisions.find((province) => province.name === 'Hà Nội')?.wards).toHaveLength(126)
})

it('filters wards by province and clears the previous ward when switching provinces', async () => {
  openPage('/me/addresses')
  await screen.findByRole('table')
  fireEvent.click(screen.getByRole('button', { name: /Thêm địa chỉ/ }))
  const dialog = within(screen.getByRole('dialog'))
  const province = dialog.getByLabelText('TỈNH / THÀNH PHỐ')
  const ward = dialog.getByLabelText('XÃ / PHƯỜNG / ĐẶC KHU')
  expect(ward).toBeDisabled()
  expect(dialog.queryByLabelText('QUẬN / HUYỆN')).not.toBeInTheDocument()
  fireEvent.change(province, { target: { value: 'Hà Nội' } })
  expect(within(ward).getByRole('option', { name: 'Phường Ba Đình' })).toBeInTheDocument()
  expect(within(ward).queryByRole('option', { name: 'Phường Sài Gòn' })).not.toBeInTheDocument()
  fireEvent.change(ward, { target: { value: 'Phường Ba Đình' } })
  fireEvent.change(province, { target: { value: 'TP. Hồ Chí Minh' } })
  expect(ward).toHaveValue('')
  expect(within(ward).getByRole('option', { name: 'Phường Sài Gòn' })).toBeInTheDocument()
  expect(within(ward).queryByRole('option', { name: 'Phường Ba Đình' })).not.toBeInTheDocument()
})

it('requires an explicit current ward when editing a legacy address', async () => {
  vi.mocked(listAddresses).mockResolvedValue([{ ...address, ward: 'Phường cũ', district: 'Quận cũ' }])
  openPage('/me/addresses')
  fireEvent.click(await screen.findByRole('button', { name: 'Chỉnh sửa' }))
  const dialog = within(screen.getByRole('dialog'))
  expect(dialog.getByText(/Địa chỉ đã lưu:/)).toHaveTextContent('Quận cũ')
  fireEvent.submit(dialog.getByRole('button', { name: 'Lưu thay đổi' }).closest('form')!)
  expect(updateAddress).not.toHaveBeenCalled()
  fireEvent.change(dialog.getByLabelText(/^XÃ \/ PHƯỜNG/), { target: { value: 'Phường Ba Đình' } })
  fireEvent.click(dialog.getByRole('button', { name: 'Lưu thay đổi' }))
  await waitFor(() => expect(updateAddress).toHaveBeenCalledWith(7,
    expect.objectContaining({ ward: 'Phường Ba Đình', district: '', province: 'Hà Nội' })))
})

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
    { target: { value: 'TP. Hồ Chí Minh' } })
  fireEvent.change(within(addDialog).getByLabelText('XÃ / PHƯỜNG / ĐẶC KHU'),
    { target: { value: 'Phường Sài Gòn' } })
  fireEvent.click(within(addDialog).getByRole('button', { name: 'Thêm địa chỉ' }))
  await waitFor(() => expect(createAddress).toHaveBeenCalledWith(expect.objectContaining({
    recipientName: 'Người nhận mới', phone: '0909999999', addressLine: '456 Đường B',
    province: 'TP. Hồ Chí Minh', ward: 'Phường Sài Gòn', district: '' })))
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
