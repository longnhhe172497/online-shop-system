import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProductDetailPage from './ProductDetailPage'
import { getProduct, type ProductDetail } from './api/catalog'
import { getMe } from './api/auth'
import { addCartItem, getCart } from './api/cart'
import { getProductReviews, type PublicReview } from './api/reviews'

vi.mock('./api/catalog', () => ({ getProduct: vi.fn(), listCategories: vi.fn() }))
vi.mock('./api/auth', () => ({ getMe: vi.fn(), logout: vi.fn() }))
vi.mock('./api/cart', () => ({ getCart: vi.fn(), addCartItem: vi.fn(), updateCartItem: vi.fn(), removeCartItem: vi.fn() }))
vi.mock('./api/reviews', () => ({ getProductReviews: vi.fn() }))

const detail: ProductDetail = { id: 1, sku: 'SKU-1', name: 'Áo sơ mi xanh', description: 'Vải cotton thoáng mát.',
  imageUrl: null, price: 1290000, categoryId: 5, categoryName: 'Áo sơ mi', availableQuantity: 2,
  avgRating: 4.5, reviewCount: 2 }

const review: PublicReview = { id: 11, authorName: 'Phạm Đ.', rating: 5, comment: 'Màu xanh rất đẹp.',
  managerReply: 'Cảm ơn bạn đã ủng hộ FORME!', repliedAt: '2026-10-30T00:00:00Z', createdAt: '2026-10-29T00:00:00Z', edited: false }

function reviewPage(items: PublicReview[], page: number, totalPages: number) {
  return { items, page, size: 5, totalItems: totalPages * 5, totalPages }
}

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/products/:id" element={<ProductDetailPage />} />
    <Route path="/checkout" element={<p>Trang thanh toán</p>} />
  </Routes></MemoryRouter>)
}

describe('ProductDetailPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getMe).mockRejectedValue(new Error('Không có phiên'))
    vi.mocked(getCart).mockResolvedValue({ items: [], itemCount: 0, subtotal: 0 })
    vi.mocked(getProductReviews).mockResolvedValue(reviewPage([], 0, 0))
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
    expect(screen.getByRole('button', { name: 'MUA NGAY' })).toBeDisabled()
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

  it('buys now: adds the chosen quantity and opens checkout', async () => {
    vi.mocked(getProduct).mockResolvedValue(detail)
    vi.mocked(addCartItem).mockResolvedValue({ items: [], itemCount: 2, subtotal: 2580000 })
    renderAt('/products/1')
    fireEvent.click(await screen.findByRole('button', { name: 'Tăng số lượng' }))
    fireEvent.click(screen.getByRole('button', { name: 'MUA NGAY' }))
    expect(await screen.findByText('Trang thanh toán')).toBeInTheDocument()
    expect(addCartItem).toHaveBeenCalledWith(1, 2)
  })

  it('stays on the product when buying now fails', async () => {
    vi.mocked(getProduct).mockResolvedValue(detail)
    vi.mocked(addCartItem).mockRejectedValue({ isAxiosError: true,
      response: { status: 409, data: { code: 'QUANTITY_LIMIT' } } })
    renderAt('/products/1')
    fireEvent.click(await screen.findByRole('button', { name: 'MUA NGAY' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Mỗi sản phẩm chỉ được đặt tối đa 99 chiếc.')
    expect(screen.queryByText('Trang thanh toán')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'MUA NGAY' })).toBeEnabled()
  })

  it('shows the server reason when adding fails', async () => {
    vi.mocked(getProduct).mockResolvedValue(detail)
    vi.mocked(addCartItem).mockRejectedValue({ isAxiosError: true,
      response: { status: 409, data: { code: 'QUANTITY_LIMIT' } } })
    renderAt('/products/1')
    fireEvent.click(await screen.findByRole('button', { name: 'THÊM VÀO GIỎ HÀNG' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Mỗi sản phẩm chỉ được đặt tối đa 99 chiếc.')
  })

  it('lists visible reviews with the shop reply and loads more on request', async () => {
    vi.mocked(getProduct).mockResolvedValue(detail)
    vi.mocked(getProductReviews).mockResolvedValueOnce(reviewPage([review], 0, 2))
      .mockResolvedValueOnce(reviewPage([{ ...review, id: 12, authorName: 'Lan', rating: 4, comment: 'Vải mềm.',
        managerReply: null, edited: true }], 1, 2))
    renderAt('/products/1')
    const list = await screen.findByRole('list', { name: 'Danh sách đánh giá' })
    expect(getProductReviews).toHaveBeenCalledWith(1, 0, 5)
    expect(within(list).getByText('Phạm Đ.')).toBeInTheDocument()
    expect(within(list).getByText('Màu xanh rất đẹp.')).toBeInTheDocument()
    expect(within(list).getByText('Cảm ơn bạn đã ủng hộ FORME!')).toBeInTheDocument()
    expect(within(list).getByRole('img', { name: '5 trên 5 sao' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Xem thêm đánh giá' }))
    expect(await within(list).findByText('Vải mềm.')).toBeInTheDocument()
    expect(getProductReviews).toHaveBeenLastCalledWith(1, 1, 5)
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
    expect(within(list).getByText('· đã chỉnh sửa')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Xem thêm đánh giá' })).not.toBeInTheDocument()
  })

  it('does not request reviews for a product that has none', async () => {
    vi.mocked(getProduct).mockResolvedValue({ ...detail, avgRating: null, reviewCount: 0 })
    renderAt('/products/1')
    expect(await screen.findByText(/Chưa có đánh giá nào cho sản phẩm này/)).toBeInTheDocument()
    expect(getProductReviews).not.toHaveBeenCalled()
  })
})
