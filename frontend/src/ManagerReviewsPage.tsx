import { useCallback, useEffect, useState } from 'react'
import { listManagerReviews, moderateReview, type ManagerReview, type ReviewStatus } from './api/reviews'
import { Stars } from './ReviewStars'
import { getUiError } from './uiError'
import './orders.css'
import './logistics.css'
import './managerCatalog.css'
import './reviews.css'

const statusNames: Record<ReviewStatus, string> = { VISIBLE: 'Đang hiển thị', HIDDEN: 'Đã ẩn' }
const statusTone: Record<ReviewStatus, string> = { VISIBLE: 'lg-ok', HIDDEN: 'lg-muted' }

function formatDate(value: string) {
  return new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
}

function ReviewCard({ review, busy, onModerate }: { review: ManagerReview; busy: boolean;
  onModerate: (change: { status?: ReviewStatus; reply?: string }, message: string) => Promise<boolean> }) {
  const [reply, setReply] = useState(review.managerReply ?? '')
  const dirty = reply.trim() !== (review.managerReply ?? '')
  const hidden = review.status === 'HIDDEN'

  return <li className={`rv-mgr-card${hidden ? ' hidden' : ''}`}>
    <div className="rv-mgr-head">
      <Stars rating={review.rating} />
      <span className={`lg-chip ${statusTone[review.status]}`}>{statusNames[review.status]}</span>
      <time dateTime={review.createdAt}>{formatDate(review.createdAt)}</time>
    </div>
    <p className="rv-mgr-product"><strong>{review.productName}</strong> <span className="catalog-sku">{review.sku}</span></p>
    <p className="rv-mgr-meta">{review.customerName} · {review.customerEmail} · Đơn {review.orderCode}</p>
    <p className={review.comment ? 'rv-mgr-comment' : 'rv-mgr-comment empty'}>{review.comment ?? 'Khách chỉ chấm sao, không viết nhận xét.'}</p>

    <label className="rv-mgr-reply">Phản hồi công khai
      <textarea maxLength={1000} value={reply} disabled={busy} placeholder="Cảm ơn bạn đã mua hàng…"
        onChange={(event) => setReply(event.target.value)} /></label>
    <div className="row-actions rv-mgr-actions">
      <button type="button" className={hidden ? 'lg-btn ghost' : 'lg-btn danger'} disabled={busy}
        aria-label={`${hidden ? 'Hiện lại' : 'Ẩn'} đánh giá của ${review.customerName}`}
        onClick={() => void onModerate({ status: hidden ? 'VISIBLE' : 'HIDDEN' },
          hidden ? 'Đã hiện lại đánh giá trên trang sản phẩm.' : 'Đã ẩn đánh giá khỏi trang sản phẩm.')}>
        {hidden ? 'Hiện lại' : 'Ẩn đánh giá'}</button>
      {review.managerReply && <button type="button" className="lg-btn ghost" disabled={busy}
        onClick={() => void onModerate({ reply: '' }, 'Đã gỡ phản hồi.')}>Gỡ phản hồi</button>}
      <button type="button" className="lg-btn primary" disabled={busy || !dirty || reply.trim() === ''}
        aria-label={`Lưu phản hồi cho ${review.customerName}`}
        onClick={() => void onModerate({ reply: reply.trim() }, 'Đã lưu phản hồi.')}>
        {review.managerReply ? 'Cập nhật phản hồi' : 'Gửi phản hồi'}</button>
    </div>
  </li>
}

/** UC-MNG-005 Manage Feedback: search, hide, restore and publicly answer customer reviews. */
export default function ManagerReviewsPage() {
  const [reviews, setReviews] = useState<ManagerReview[]>([])
  const [loaded, setLoaded] = useState(false)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<'' | ReviewStatus>('')
  const [ratingFilter, setRatingFilter] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    try {
      const data = await listManagerReviews(page, { q: search.trim() || undefined,
        status: statusFilter || undefined, rating: ratingFilter ? Number(ratingFilter) : undefined })
      setReviews(data.items); setTotalItems(data.totalItems); setTotalPages(data.totalPages); setError('')
    } catch (failure) { setError(getUiError(failure)) }
    finally { setLoaded(true) }
  }, [page, search, statusFilter, ratingFilter])
  useEffect(() => { const timer = window.setTimeout(() => { void load() }, 250); return () => window.clearTimeout(timer) }, [load])

  async function moderate(review: ManagerReview, change: { status?: ReviewStatus; reply?: string }, message: string) {
    setBusyId(review.id); setError(''); setNotice('')
    try {
      const saved = await moderateReview(review.id, change)
      setReviews((current) => current.map((item) => item.id === saved.id ? saved : item))
      setNotice(message)
      return true
    } catch (failure) { setError(getUiError(failure)); return false }
    finally { setBusyId(null) }
  }

  return <section className="admin-panel">
    <div className="admin-section-head"><div>
      <p>Chỉ khách đã nhận hàng mới đánh giá được. Đánh giá bị ẩn không hiển thị và không tính vào điểm sản phẩm.</p></div></div>
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}

    <div className="admin-filters catalog-filters">
      <input type="search" aria-label="Tìm đánh giá" placeholder="Tìm sản phẩm, đơn, khách…"
        value={search} maxLength={100} onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
      <select aria-label="Lọc theo trạng thái" value={statusFilter}
        onChange={(event) => { setStatusFilter(event.target.value as '' | ReviewStatus); setPage(0) }}>
        <option value="">Mọi trạng thái</option>
        <option value="VISIBLE">{statusNames.VISIBLE}</option>
        <option value="HIDDEN">{statusNames.HIDDEN}</option>
      </select>
      <select aria-label="Lọc theo số sao" value={ratingFilter}
        onChange={(event) => { setRatingFilter(event.target.value); setPage(0) }}>
        <option value="">Mọi số sao</option>
        {[5, 4, 3, 2, 1].map((level) => <option key={level} value={level}>{level} sao</option>)}
      </select>
    </div>

    {loaded && reviews.length === 0 && !error && <p className="admin-empty">Chưa có đánh giá phù hợp.</p>}
    {reviews.length > 0 && <ul className="rv-mgr-list" aria-label="Đánh giá của khách hàng">
      {reviews.map((review) => <ReviewCard key={`${review.id}:${review.repliedAt ?? ''}`} review={review}
        busy={busyId === review.id} onModerate={(change, message) => moderate(review, change, message)} />)}
    </ul>}
    {totalPages > 1 && <div className="admin-pagination">
      <button type="button" className="account-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
      <span>Trang {page + 1}/{totalPages} · {totalItems} đánh giá</span>
      <button type="button" className="account-secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
    </div>}
  </section>
}
