import { isAxiosError } from 'axios'
import { api } from './client'
import type { Page } from './products'

export type ReviewStatus = 'VISIBLE' | 'HIDDEN'

/** Shown on the product page. authorName is already shortened by the server ("Phạm Đ."). */
export interface PublicReview {
  id: number
  authorName: string
  rating: number
  comment: string | null
  managerReply: string | null
  repliedAt: string | null
  createdAt: string
  edited: boolean
}

/** The signed-in customer's own review of one order item. */
export interface MyReview {
  id: number
  orderItemId: number
  productId: number
  rating: number
  comment: string | null
  status: ReviewStatus
  managerReply: string | null
  createdAt: string
  updatedAt: string
}

export interface ManagerReview {
  id: number
  productId: number
  productName: string
  sku: string
  orderId: number
  orderCode: string
  customerName: string
  customerEmail: string
  rating: number
  comment: string | null
  status: ReviewStatus
  managerReply: string | null
  repliedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ReviewInput {
  rating: number
  comment: string | null
}

export interface ManagerReviewFilters {
  status?: ReviewStatus
  rating?: number
  q?: string
}

export async function getProductReviews(productId: number, page = 0, size = 5) {
  const { data } = await api.get<Page<PublicReview>>(`/products/${productId}/reviews`, { params: { page, size } })
  return data
}

/** null when the item has not been reviewed yet. */
export async function getMyReview(orderItemId: number): Promise<MyReview | null> {
  try {
    const { data } = await api.get<MyReview>(`/order-items/${orderItemId}/review`)
    return data
  } catch (error) {
    if (isAxiosError(error) && error.response?.status === 404
      && (error.response.data as { code?: string } | undefined)?.code === 'REVIEW_NOT_FOUND') return null
    throw error
  }
}

export async function createReview(orderItemId: number, input: ReviewInput) {
  const { data } = await api.post<MyReview>(`/order-items/${orderItemId}/review`, input)
  return data
}

export async function updateReview(orderItemId: number, input: ReviewInput) {
  const { data } = await api.patch<MyReview>(`/order-items/${orderItemId}/review`, input)
  return data
}

export async function listManagerReviews(page: number, filters: ManagerReviewFilters = {}) {
  const { data } = await api.get<Page<ManagerReview>>('/manager/reviews', { params: { page, size: 20, ...filters } })
  return data
}

/** Send status, reply or both. An empty reply removes the published reply. */
export async function moderateReview(id: number, change: { status?: ReviewStatus; reply?: string }) {
  const { data } = await api.patch<ManagerReview>(`/manager/reviews/${id}`, change)
  return data
}
