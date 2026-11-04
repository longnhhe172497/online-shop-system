import { useEffect, useState } from 'react'
import { getProductReviews, type PublicReview } from './api/reviews'
import { Stars } from './ReviewStars'
import { getUiError } from './uiError'

const PAGE_SIZE = 5

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('vi-VN', { day: 'numeric', month: 'long', year: 'numeric' })
}

/** Product page reviews (UC-CUS-015): VISIBLE reviews only, newest first, with the shop's public reply. */
export default function ProductReviews({ productId, avgRating, reviewCount }: {
  productId: number; avgRating: number | null; reviewCount: number }) {
  const [reviews, setReviews] = useState<PublicReview[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(reviewCount > 0)
  const [error, setError] = useState('')

  useEffect(() => {
    if (reviewCount === 0) return
    let active = true
    getProductReviews(productId, page, PAGE_SIZE)
      .then((data) => {
        if (!active) return
        setReviews((current) => page === 0 ? data.items : [...current, ...data.items])
        setTotalPages(data.totalPages); setError('')
      })
      .catch((failure: unknown) => { if (active) setError(getUiError(failure)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [productId, page, reviewCount])

  return <section className="fs-reviews" aria-labelledby="fs-reviews-title">
    <div className="fs-pagehead">
      <div><p className="fs-eyebrow">Ý kiến khách hàng</p><h2 id="fs-reviews-title">Đánh giá</h2></div>
      {reviewCount > 0 && avgRating !== null && <span className="rv-summary">
        <Stars rating={avgRating} />
        <b>{avgRating.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}</b> ({reviewCount})</span>}
    </div>

    {reviewCount === 0 && <p className="fs-muted">Chưa có đánh giá nào cho sản phẩm này.
      Khách hàng đã nhận hàng có thể đánh giá trong trang chi tiết đơn hàng.</p>}
    {error && <p className="fs-state" role="alert">{error}</p>}

    {reviews.length > 0 && <ul className="rv-list" aria-label="Danh sách đánh giá">
      {reviews.map((review) => <li key={review.id} className="rv-item">
        <div className="rv-item-head">
          <Stars rating={review.rating} />
          <b>{review.authorName}</b>
          <span className="rv-verified">✓ Đã mua hàng</span>
          <time dateTime={review.createdAt}>{formatDate(review.createdAt)}</time>
          {review.edited && <span className="fs-muted">· đã chỉnh sửa</span>}
        </div>
        {review.comment && <p className="rv-comment">{review.comment}</p>}
        {review.managerReply && <div className="rv-reply">
          <b>Phản hồi từ FORME</b>
          <p>{review.managerReply}</p>
        </div>}
      </li>)}
    </ul>}

    {loading && <p className="fs-state">Đang tải đánh giá…</p>}
    {!loading && page + 1 < totalPages && <button type="button" className="fs-btn ghost rv-more"
      onClick={() => { setLoading(true); setPage(page + 1) }}>Xem thêm đánh giá</button>}
  </section>
}
