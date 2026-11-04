import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OrderItemReview from './OrderItemReview'
import { createReview, getMyReview, updateReview, type MyReview } from './api/reviews'

vi.mock('./api/reviews', () => ({ getMyReview: vi.fn(), createReview: vi.fn(), updateReview: vi.fn() }))

const mine: MyReview = { id: 3, orderItemId: 7, productId: 1, rating: 4, comment: 'Vải mềm', status: 'VISIBLE',
  managerReply: null, createdAt: '2026-10-29T00:00:00Z', updatedAt: '2026-10-29T00:00:00Z' }

function renderItem() {
  return render(<OrderItemReview orderItemId={7} productName="Áo sơ mi xanh" />)
}

describe('OrderItemReview', () => {
  beforeEach(() => { vi.resetAllMocks() })
  afterEach(cleanup)

  it('lets the customer rate a delivered item and shows the saved review', async () => {
    vi.mocked(getMyReview).mockResolvedValue(null)
    vi.mocked(createReview).mockResolvedValue({ ...mine, rating: 5, comment: 'Đúng màu' })
    renderItem()
    fireEvent.click(await screen.findByRole('button', { name: 'Đánh giá Áo sơ mi xanh' }))

    // A star rating is required.
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đánh giá' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Hãy chọn số sao trước khi gửi.')
    expect(createReview).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('radio', { name: '5 sao' }))
    expect(screen.getByText('Tuyệt vời')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('textbox', { name: /Nhận xét/ }), { target: { value: '  Đúng màu  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đánh giá' }))

    await waitFor(() => expect(createReview).toHaveBeenCalledWith(7, { rating: 5, comment: 'Đúng màu' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Cảm ơn bạn đã đánh giá sản phẩm!')
    expect(screen.getByRole('img', { name: '5 trên 5 sao' })).toBeInTheDocument()
    expect(screen.getByText('Đúng màu')).toBeInTheDocument()
  })

  it('edits an existing review instead of creating a second one', async () => {
    vi.mocked(getMyReview).mockResolvedValue({ ...mine, status: 'HIDDEN', managerReply: 'Shop đã ghi nhận.' })
    vi.mocked(updateReview).mockResolvedValue({ ...mine, rating: 2, comment: null })
    renderItem()
    expect(await screen.findByText('Đánh giá này đang được cửa hàng ẩn khỏi trang sản phẩm.')).toBeInTheDocument()
    expect(screen.getByText('Shop đã ghi nhận.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đánh giá Áo sơ mi xanh' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Sửa đánh giá Áo sơ mi xanh' }))
    expect(screen.getByRole('radio', { name: '4 sao' })).toBeChecked()
    fireEvent.click(screen.getByRole('radio', { name: '2 sao' }))
    fireEvent.change(screen.getByRole('textbox', { name: /Nhận xét/ }), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu đánh giá' }))

    await waitFor(() => expect(updateReview).toHaveBeenCalledWith(7, { rating: 2, comment: null }))
    expect(createReview).not.toHaveBeenCalled()
    expect(await screen.findByRole('status')).toHaveTextContent('Đã cập nhật đánh giá.')
  })

  it('switches to the existing review when it was already submitted elsewhere', async () => {
    vi.mocked(getMyReview).mockResolvedValueOnce(null).mockResolvedValueOnce(mine)
    vi.mocked(createReview).mockRejectedValue({ isAxiosError: true,
      response: { status: 409, data: { code: 'REVIEW_ALREADY_EXISTS' } } })
    renderItem()
    fireEvent.click(await screen.findByRole('button', { name: 'Đánh giá Áo sơ mi xanh' }))
    fireEvent.click(screen.getByRole('radio', { name: '3 sao' }))
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đánh giá' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bạn đã đánh giá sản phẩm này cho đơn hàng này')
    // The form now saves through PATCH.
    expect(await screen.findByRole('button', { name: 'Lưu đánh giá' })).toBeInTheDocument()
  })
})
