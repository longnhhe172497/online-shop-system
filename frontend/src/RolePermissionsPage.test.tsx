import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RolePermissionsPage from './RolePermissionsPage'
import { getRolePermissions, updateRolePermissions } from './api/admin'

vi.mock('./api/admin', () => ({ getRolePermissions: vi.fn(), updateRolePermissions: vi.fn() }))

describe('RolePermissionsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getRolePermissions).mockResolvedValue({
      catalog: [{ code: 'MANAGER_ORDER_VIEW', label: 'Xem đơn hàng', group: 'Đơn hàng',
        endpoint: 'GET /manager/orders' },
      { code: 'WAREHOUSE_INVENTORY_VIEW', label: 'Xem tồn kho', group: 'Kho',
        endpoint: 'GET /warehouse/inventory' }],
      roles: [{ role: 'MANAGER', version: 2, permissions: ['MANAGER_ORDER_VIEW'] },
        { role: 'SUPPORT', version: 0, permissions: [] },
        { role: 'WAREHOUSE', version: 0, permissions: ['WAREHOUSE_INVENTORY_VIEW'] },
        { role: 'DELIVERY', version: 0, permissions: [] }],
    })
  })

  it('shows a confirmation and sends only the selected role permissions with its version', async () => {
    vi.mocked(updateRolePermissions).mockResolvedValue({ role: 'MANAGER', version: 3,
      permissions: ['MANAGER_ORDER_VIEW', 'WAREHOUSE_INVENTORY_VIEW'] })
    render(<RolePermissionsPage />)
    fireEvent.click(await screen.findByRole('checkbox', { name: /Xem tồn kho/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
    const dialog = screen.getByRole('dialog', { name: 'Cập nhật Quản lý?' })
    expect(within(dialog).getByText(/tất cả tài khoản mang vai trò này/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận lưu' }))
    await waitFor(() => expect(updateRolePermissions).toHaveBeenCalledWith('MANAGER', 2,
      ['MANAGER_ORDER_VIEW', 'WAREHOUSE_INVENTORY_VIEW']))
    expect(await screen.findByText(/Đã lưu quyền/)).toBeInTheDocument()
  })
})
