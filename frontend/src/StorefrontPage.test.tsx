import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import StorefrontPage from './StorefrontPage'
import { listProducts, type Product } from './api/products'
import { listCategories } from './api/catalog'
import { getMe } from './api/auth'

vi.mock('./api/products', () => ({ listProducts: vi.fn() }))
vi.mock('./api/catalog', () => ({ listCategories: vi.fn(), getProduct: vi.fn() }))
vi.mock('./api/auth', () => ({ getMe: vi.fn(), logout: vi.fn() }))

const shirt: Product = { id: 1, sku: 'SKU-1', name: 'Áo sơ mi xanh', description: null, imageUrl: null,
  price: 1290000, categoryName: 'Áo sơ mi', availableQuantity: 3 }

function pageOf(items: Product[], totalPages = 1) {
  return { items, page: 0, size: 12, totalItems: items.length, totalPages }
}

function renderAt(path = '/') {
  return render(<MemoryRouter initialEntries={[path]}><StorefrontPage /></MemoryRouter>)
}

describe('StorefrontPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getMe).mockRejectedValue(new Error('Không có phiên'))
    vi.mocked(listCategories).mockResolvedValue([{ id: 5, name: 'Áo sơ mi', description: null }])
    vi.mocked(listProducts).mockResolvedValue(pageOf([shirt]))
  })
  afterEach(cleanup)

  it('shows products with price and a link to the detail page', async () => {
    renderAt()
    expect(await screen.findByText('Áo sơ mi xanh')).toBeInTheDocument()
    expect(screen.getByText('1.290.000 ₫')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Áo sơ mi xanh/ })).toHaveAttribute('href', '/products/1')
    expect(listProducts).toHaveBeenCalledWith(0, 12, expect.objectContaining({ sort: 'newest' }))
  })

  it('sends search, category and price filters to the API', async () => {
    renderAt()
    await screen.findByText('Áo sơ mi xanh')
    fireEvent.change(screen.getByRole('searchbox', { name: 'Tìm sản phẩm' }), { target: { value: 'xanh' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tìm' }))
    await waitFor(() => expect(listProducts).toHaveBeenLastCalledWith(0, 12, expect.objectContaining({ q: 'xanh' })))

    await screen.findByRole('option', { name: 'Áo sơ mi' })
    fireEvent.change(screen.getByRole('combobox', { name: 'Danh mục' }), { target: { value: '5' } })
    await waitFor(() => expect(listProducts).toHaveBeenLastCalledWith(0, 12,
      expect.objectContaining({ q: 'xanh', categoryId: 5 })))

    fireEvent.change(screen.getByRole('combobox', { name: 'Mức giá' }), { target: { value: '500-1000' } })
    await waitFor(() => expect(listProducts).toHaveBeenLastCalledWith(0, 12,
      expect.objectContaining({ minPrice: 500000, maxPrice: 1000000 })))
  })

  it('offers to clear filters when nothing matches', async () => {
    vi.mocked(listProducts).mockResolvedValue(pageOf([]))
    renderAt('/?q=khong-co')
    expect(await screen.findByText('Không tìm thấy sản phẩm phù hợp.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Xóa bộ lọc' }))
    await waitFor(() => expect(listProducts).toHaveBeenLastCalledWith(0, 12, expect.objectContaining({ q: undefined })))
  })

  it('shows an error when products cannot be loaded', async () => {
    vi.mocked(listProducts).mockRejectedValue(new Error('down'))
    renderAt()
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải sản phẩm')
  })

  it('moves to the next page', async () => {
    vi.mocked(listProducts).mockResolvedValue(pageOf([shirt], 3))
    renderAt()
    fireEvent.click(await screen.findByRole('button', { name: 'Trang sau' }))
    await waitFor(() => expect(listProducts).toHaveBeenLastCalledWith(1, 12, expect.anything()))
  })
})