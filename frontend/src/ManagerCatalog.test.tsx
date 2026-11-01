import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'
import ManagerProductsPage from './ManagerProductsPage'
import ManagerCategoriesPage from './ManagerCategoriesPage'
import { getProfile } from './api/profile'
import { changeCategoryStatus, changeProductStatus, createCategory, createProduct, deleteCategory,
  listCategoryOptions, listManagerCategories, listManagerProducts, type ManagerCategory,
  type ManagerProduct } from './api/managerCatalog'

vi.mock('./api/profile', () => ({ getProfile: vi.fn() }))
vi.mock('./SignOutButton', () => ({ default: () => null }))
vi.mock('./api/managerCatalog', () => ({ listManagerProducts: vi.fn(), listCategoryOptions: vi.fn(),
  createProduct: vi.fn(), updateProduct: vi.fn(), changeProductStatus: vi.fn(), listManagerCategories: vi.fn(),
  createCategory: vi.fn(), updateCategory: vi.fn(), changeCategoryStatus: vi.fn(), deleteCategory: vi.fn() }))

const shirt: ManagerProduct = { id: 4, sku: 'AO-01', name: 'Áo sơ mi xanh', description: null, imageUrl: null,
  price: 1290000, categoryId: 2, categoryName: 'Áo sơ mi', categoryStatus: 'ACTIVE', status: 'DRAFT',
  stockOnHand: 9, stockReserved: 4, availableQuantity: 5, createdAt: '2026-10-28T00:00:00Z', updatedAt: '2026-10-28T00:00:00Z' }
const shirts: ManagerCategory = { id: 2, name: 'Áo sơ mi', description: 'Áo công sở', status: 'ACTIVE',
  productCount: 3, activeProductCount: 1, createdAt: '2026-10-28T00:00:00Z', updatedAt: '2026-10-28T00:00:00Z' }
const empty: ManagerCategory = { ...shirts, id: 3, name: 'Phụ kiện', description: null, productCount: 0, activeProductCount: 0 }

const manager = { id: 1, email: 'manager@example.com', fullName: 'Quản lý', phone: null, role: 'MANAGER' }

function CurrentPath() {
  return <output data-testid="path">{useLocation().pathname}</output>
}

function pageOf(items: ManagerProduct[]) {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 }
}

function conflict(code: string) {
  return { isAxiosError: true, response: { status: 409, data: { code } } }
}

describe('Manager catalog', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(listCategoryOptions).mockResolvedValue([{ id: 2, name: 'Áo sơ mi', status: 'ACTIVE' },
      { id: 5, name: 'Quần', status: 'INACTIVE' }])
    vi.mocked(listManagerProducts).mockResolvedValue(pageOf([shirt]))
    vi.mocked(listManagerCategories).mockResolvedValue([shirts, empty])
  })
  afterEach(cleanup)

  it('opens product and category management at /manager/products and /manager/categories', async () => {
    vi.mocked(getProfile).mockResolvedValue(manager)
    render(<MemoryRouter initialEntries={['/manager']}><AdminPage /><CurrentPath /></MemoryRouter>)
    const nav = await screen.findByRole('navigation', { name: 'Điều hướng nội bộ' })
    expect(within(nav).getByRole('button', { name: 'Mã giảm giá' })).toBeInTheDocument()

    fireEvent.click(within(nav).getByRole('button', { name: 'Sản phẩm' }))
    expect(await screen.findByRole('heading', { name: 'Quản lý sản phẩm', level: 1 })).toBeInTheDocument()
    expect(screen.getByTestId('path')).toHaveTextContent('/manager/products')
    expect(await screen.findByText('Áo sơ mi xanh')).toBeInTheDocument()

    fireEvent.click(within(nav).getByRole('button', { name: 'Danh mục' }))
    expect(await screen.findByRole('heading', { name: 'Quản lý danh mục', level: 1 })).toBeInTheDocument()
    expect(screen.getByTestId('path')).toHaveTextContent('/manager/categories')
  })

  it('opens the category screen directly from its URL', async () => {
    vi.mocked(getProfile).mockResolvedValue(manager)
    render(<MemoryRouter initialEntries={['/manager/categories']}><AdminPage /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Quản lý danh mục', level: 1 })).toBeInTheDocument()
    expect(await screen.findByText('1 đang bán')).toBeInTheDocument()
  })

  it('keeps catalog tools away from other staff roles', async () => {
    vi.mocked(getProfile).mockResolvedValue({ ...manager, role: 'SUPPORT' })
    render(<MemoryRouter initialEntries={['/support/products']}><AdminPage /><CurrentPath /></MemoryRouter>)
    const nav = await screen.findByRole('navigation', { name: 'Điều hướng nội bộ' })
    expect(within(nav).queryByRole('button', { name: 'Sản phẩm' })).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('path')).toHaveTextContent(/^\/support$/))
    expect(listManagerProducts).not.toHaveBeenCalled()
  })

  it('lists products with read-only stock and filters by status', async () => {
    render(<ManagerProductsPage />)
    expect(await screen.findByText('AO-01')).toBeInTheDocument()
    expect(screen.getByText('5 có thể bán')).toBeInTheDocument()
    expect(screen.getByText('9 trong kho · 4 đang giữ')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('combobox', { name: 'Lọc theo trạng thái' }), { target: { value: 'ACTIVE' } })
    await waitFor(() => expect(listManagerProducts).toHaveBeenLastCalledWith(0, expect.objectContaining({ status: 'ACTIVE' })))
  })

  it('creates a product without any stock field', async () => {
    vi.mocked(createProduct).mockResolvedValue({ ...shirt, id: 9 })
    render(<ManagerProductsPage />)
    await screen.findByText('AO-01')
    fireEvent.click(screen.getByRole('button', { name: '+ Thêm sản phẩm' }))
    const dialog = screen.getByRole('dialog', { name: 'Thêm sản phẩm' })
    expect(within(dialog).getByText(/tồn kho 0/)).toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText('Mã SKU'), { target: { value: ' quan-02 ' } })
    fireEvent.change(within(dialog).getByLabelText('Tên sản phẩm'), { target: { value: 'Quần ống rộng' } })
    fireEvent.change(within(dialog).getByLabelText('Giá bán (₫)'), { target: { value: '890000' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(createProduct).toHaveBeenCalledWith({ sku: 'quan-02', name: 'Quần ống rộng',
      description: null, imageUrl: null, price: 890000, categoryId: 2 }))
    expect(await screen.findByText('Đã tạo Quần ống rộng ở trạng thái Nháp.')).toBeInTheDocument()
  })

  it('shows the reason when a duplicate SKU is saved', async () => {
    vi.mocked(createProduct).mockRejectedValue(conflict('SKU_ALREADY_EXISTS'))
    render(<ManagerProductsPage />)
    await screen.findByText('AO-01')
    fireEvent.click(screen.getByRole('button', { name: '+ Thêm sản phẩm' }))
    const dialog = screen.getByRole('dialog', { name: 'Thêm sản phẩm' })
    fireEvent.change(within(dialog).getByLabelText('Mã SKU'), { target: { value: 'AO-01' } })
    fireEvent.change(within(dialog).getByLabelText('Tên sản phẩm'), { target: { value: 'Trùng' } })
    fireEvent.change(within(dialog).getByLabelText('Giá bán (₫)'), { target: { value: '1000' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Mã SKU này đã được dùng')
  })

  it('asks before archiving and explains a refused publish', async () => {
    vi.mocked(changeProductStatus).mockRejectedValueOnce(conflict('CATEGORY_NOT_ACTIVE'))
      .mockResolvedValue({ ...shirt, status: 'ARCHIVED' })
    render(<ManagerProductsPage />)
    const status = await screen.findByRole('combobox', { name: 'Đổi trạng thái Áo sơ mi xanh' })

    fireEvent.change(status, { target: { value: 'ACTIVE' } })
    expect(await screen.findByRole('alert')).toHaveTextContent('Danh mục đang tạm ẩn')

    fireEvent.change(status, { target: { value: 'ARCHIVED' } })
    const confirm = await screen.findByRole('dialog', { name: 'Lưu trữ Áo sơ mi xanh?' })
    expect(changeProductStatus).toHaveBeenCalledTimes(1)
    fireEvent.click(within(confirm).getByRole('button', { name: 'Lưu trữ' }))
    await waitFor(() => expect(changeProductStatus).toHaveBeenLastCalledWith(4, 'ARCHIVED'))
  })

  it('only lets an empty category be deleted, after confirmation', async () => {
    vi.mocked(deleteCategory).mockResolvedValue(undefined)
    render(<ManagerCategoriesPage />)
    expect(await screen.findByText('1 đang bán')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xóa Áo sơ mi' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Xóa Phụ kiện' }))
    const dialog = screen.getByRole('dialog', { name: 'Xóa danh mục Phụ kiện?' })
    expect(deleteCategory).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xóa' }))
    await waitFor(() => expect(deleteCategory).toHaveBeenCalledWith(3))
    expect(await screen.findByText('Đã xóa danh mục Phụ kiện.')).toBeInTheDocument()
  })

  it('creates categories and explains refused changes', async () => {
    vi.mocked(createCategory).mockRejectedValue(conflict('CATEGORY_NAME_EXISTS'))
    vi.mocked(changeCategoryStatus).mockRejectedValue(conflict('CATEGORY_HAS_PRODUCTS'))
    render(<ManagerCategoriesPage />)
    await screen.findByText('1 đang bán')

    fireEvent.click(screen.getByRole('button', { name: '+ Thêm danh mục' }))
    const dialog = screen.getByRole('dialog', { name: 'Thêm danh mục' })
    fireEvent.change(within(dialog).getByLabelText('Tên danh mục'), { target: { value: ' Áo sơ mi ' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(createCategory).toHaveBeenCalledWith({ name: 'Áo sơ mi', description: null }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Tên danh mục đã tồn tại.')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Hủy' }))

    fireEvent.change(screen.getByRole('combobox', { name: 'Đổi trạng thái Áo sơ mi' }), { target: { value: 'ARCHIVED' } })
    fireEvent.click(within(screen.getByRole('dialog', { name: 'Lưu trữ danh mục Áo sơ mi?' }))
      .getByRole('button', { name: 'Lưu trữ' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Danh mục vẫn còn sản phẩm')
  })
})
