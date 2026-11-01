import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProductDetailPage from './ProductDetailPage'
import { getProduct, type ProductDetail } from './api/catalog'
import { getMe } from './api/auth'
import { addCartItem, getCart } from './api/cart'

vi.mock('./api/catalog', () => ({ getProduct: vi.fn(), listCategories: vi.fn() }))
vi.mock('./api/auth', () => ({ getMe: vi.fn(), logout: vi.fn() }))
vi.mock('./api/cart', () => ({ getCart: vi.fn(), addCartItem: vi.fn(), updateCartItem: vi.fn(), removeCartItem: vi.fn() }))

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
    vi.mocked(getCart).mockResolvedValue({ items: [], itemCount: 0, subtotal: 0 })
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

  it('adds the chosen quantity to the cart', async () => {
    vi.mocked(getProduct).mockResolvedValue(detail)
    vi.mocked(addCartItem).mockResolvedValue({ items: [], itemCount: 2, subtotal: 2580000 })
    renderAt('/products/1')
    fireEvent.click(await screen.findByRole('button', { name: 'Tăng số lượng' }))
    fireEvent.click(screen.getByRole('button', { name: 'THÊM VÀO GIỎ HÀNG' }))
    expect(await screen.findByText('Đã thêm vào giỏ hàng.')).toBeInTheDocument()
    expect(addCartItem).toHaveBeenCalledWith(1, 2)
    expect(screen.getByRole('link', { name: 'Xem giỏ hàng' })).toHaveAttribute('href', '/cart')
  })

  it('shows the server reason when adding fails', async () => {
    vi.mocked(getProduct).mockResolvedValue(detail)
    vi.mocked(addCartItem).mockRejectedValue({ isAxiosError: true,
      response: { status: 409, data: { code: 'QUANTITY_LIMIT' } } })
    renderAt('/products/1')
    fireEvent.click(await screen.findByRole('button', { name: 'THÊM VÀO GIỎ HÀNG' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Mỗi sản phẩm chỉ được đặt tối đa 99 chiếc.')
  })
})
