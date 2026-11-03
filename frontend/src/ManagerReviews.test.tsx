import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'
import ManagerReviewsPage from './ManagerReviewsPage'
import { getProfile } from './api/profile'
import { listManagerReviews, moderateReview, type ManagerReview } from './api/reviews'

vi.mock('./api/profile', () => ({ getProfile: vi.fn() }))
vi.mock('./SignOutButton', () => ({ default: () => null }))
vi.mock('./api/reviews', () => ({ listManagerReviews: vi.fn(), moderateReview: vi.fn() }))

const review: ManagerReview = { id: 9, productId: 1, productName: 'Áo sơ mi xanh', sku: 'AO-01', orderId: 4,
  orderCode: 'FM-1001', customerName: 'Trần Bảo', customerEmail: 'bao@example.com', rating: 2, comment: 'Hơi rộng',
  status: 'VISIBLE', managerReply: null, repliedAt: null, createdAt: '2026-10-29T03:00:00Z', updatedAt: '2026-10-29T03:00:00Z' }

function pageOf(items: ManagerReview[]) {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 }
}

function CurrentPath() {
  return <output data-testid="path">{useLocation().pathname}</output>
}

describe('Manager reviews', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(listManagerReviews).mockResolvedValue(pageOf([review]))
  })
  afterEach(cleanup)

  it('opens review moderation at /manager/reviews from the internal tabs', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 1, email: 'm@example.com', fullName: 'Quản lý', phone: null, role: 'MANAGER' })
    render(<MemoryRouter initialEntries={['/manager']}><AdminPage /><CurrentPath /></MemoryRouter>)
    const nav = await screen.findByRole('navigation', { name: 'Điều hướng nội bộ' })
    fireEvent.click(within(nav).getByRole('button', { name: 'Đánh giá' }))
    expect(await screen.findByRole('heading', { name: 'Đánh giá sản phẩm', level: 1 })).toBeInTheDocument()
    expect(screen.getByTestId('path')).toHaveTextContent('/manager/reviews')
    expect(await screen.findByText('Hơi rộng')).toBeInTheDocument()
  })

  it('is not offered to Support staff', async () => {
    vi.mocked(getProfile).mockResolvedValue({ id: 2, email: 's@example.com', fullName: 'Hỗ trợ', phone: null, role: 'SUPPORT' })
    render(<MemoryRouter initialEntries={['/support/reviews']}><AdminPage /></MemoryRouter>)
    const nav = await screen.findByRole('navigation', { name: 'Điều hướng nội bộ' })
    expect(within(nav).queryByRole('button', { name: 'Đánh giá' })).not.toBeInTheDocument()
    expect(listManagerReviews).not.toHaveBeenCalled()
  })

  it('filters, hides, restores and replies to a review', async () => {
    vi.mocked(moderateReview)
      .mockResolvedValueOnce({ ...review, status: 'HIDDEN' })
      .mockResolvedValueOnce({ ...review, status: 'VISIBLE' })
      .mockResolvedValueOnce({ ...review, managerReply: 'Shop sẽ tư vấn size kỹ hơn.', repliedAt: '2026-10-30T00:00:00Z' })
    render(<ManagerReviewsPage />)
    expect(await screen.findByText('Hơi rộng')).toBeInTheDocument()
    expect(screen.getByText('Trần Bảo · bao@example.com · Đơn FM-1001')).toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: 'Lọc theo số sao' }), { target: { value: '2' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'Lọc theo trạng thái' }), { target: { value: 'VISIBLE' } })
    await waitFor(() => expect(listManagerReviews).toHaveBeenLastCalledWith(0, { q: undefined, status: 'VISIBLE', rating: 2 }))

    fireEvent.click(screen.getByRole('button', { name: 'Ẩn đánh giá của Trần Bảo' }))
    await waitFor(() => expect(moderateReview).toHaveBeenCalledWith(9, { status: 'HIDDEN' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã ẩn đánh giá khỏi trang sản phẩm.')
    expect(screen.getByText('Đã ẩn', { selector: '.lg-chip' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Hiện lại đánh giá của Trần Bảo' }))
    await waitFor(() => expect(moderateReview).toHaveBeenLastCalledWith(9, { status: 'VISIBLE' }))

    const save = screen.getByRole('button', { name: 'Lưu phản hồi cho Trần Bảo' })
    expect(save).toBeDisabled()
    fireEvent.change(screen.getByRole('textbox', { name: 'Phản hồi công khai' }),
      { target: { value: ' Shop sẽ tư vấn size kỹ hơn. ' } })
    fireEvent.click(save)
    await waitFor(() => expect(moderateReview).toHaveBeenLastCalledWith(9, { reply: 'Shop sẽ tư vấn size kỹ hơn.' }))
    expect(await screen.findByRole('button', { name: 'Gỡ phản hồi' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Phản hồi công khai' })).toHaveValue('Shop sẽ tư vấn size kỹ hơn.')
  })
})
