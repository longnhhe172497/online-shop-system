import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProductDetailPage from './ProductDetailPage'
import { getProduct, type ProductDetail } from './api/catalog'
import { getMe } from './api/auth'

vi.mock('./api/catalog', () => ({ getProduct: vi.fn(), listCategories: vi.fn() }))
vi.mock('./api/auth', () => ({ getMe: vi.fn(), logout: vi.fn() }))

const detail: ProductDetail = { id: 1, sku: 'SKU-1', name: 'Áo sơ mi xanh', description: 'Vải cotton thoáng mát.',
  imageUrl: null, price: 1290000, categoryId: 5, categoryName: 'Áo sơ mi', availableQuantity: 2,
  avgRating: 4.5, reviewCount: 2 }

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/products/:id" element={<ProductDetailPage />} />
  </Routes></MemoryRouter>)
}

describe('ProductDetailPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getMe).mockRejectedValue(new Error('Không có phiên'))
  })
  afterEach(cleanup)

  it('shows product detail, rating and stock', async () => {
    vi.mocked(getProduct).mockResolvedValue(detail)
    renderAt('/products/1')
    expect(await screen.findByRole('heading', { name: 'Áo sơ mi xanh', level: 1 })).toBeInTheDocument()
    expect(getProduct).toHaveBeenCalledWith(1)
    expect(screen.getByText('1.290.000 ₫')).toBeInTheDocument()
    expect(screen.getByText('4,5 / 5 · 2 đánh giá')).toBeInTheDocument()
    expect(screen.getByText('Còn hàng · còn 2 sản phẩm')).toBeInTheDocument()
  })

  it('limits the quantity to available stock', async () => {
    vi.mocked(getProduct).mockResolvedValue(detail)
    renderAt('/products/1')
    const increase = await screen.findByRole('button', { name: 'Tăng số lượng' })
    fireEvent.click(increase)
    expect(screen.getByRole('group', { name: 'SỐ LƯỢNG' })).toHaveTextContent('2')
    expect(increase).toBeDisabled()
  })

  it('shows out of stock', async () => {
    vi.mocked(getProduct).mockResolvedValue({ ...detail, availableQuantity: 0, avgRating: null, reviewCount: 0 })
    renderAt('/products/1')
    expect(await screen.findByText('Tạm hết hàng')).toBeInTheDocument()
    expect(screen.getByText('Chưa có đánh giá')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'THÊM VÀO GIỎ HÀNG' })).toBeDisabled()
  })

  it('shows not found for a missing product', async () => {
    vi.mocked(getProduct).mockRejectedValue({ isAxiosError: true, response: { status: 404 } })
    renderAt('/products/99')
    expect(await screen.findByRole('heading', { name: 'Không tìm thấy sản phẩm' })).toBeInTheDocument()
  })

  it('does not call the API for an invalid id', () => {
    renderAt('/products/abc')
    expect(screen.getByRole('heading', { name: 'Không tìm thấy sản phẩm' })).toBeInTheDocument()
    expect(getProduct).not.toHaveBeenCalled()
  })
})