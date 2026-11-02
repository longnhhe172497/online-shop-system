import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { createReview, getMyReview, updateReview, type MyReview } from './api/reviews'
import { StarPicker, Stars } from './ReviewStars'
import { getUiError } from './uiError'

type State = { kind: 'loading' } | { kind: 'failed'; message: string } | { kind: 'ready'; review: MyReview | null }

/**
 * "Rate / Review" for one line of a DELIVERED order (UC-CUS-015, extension of View Order Details).
 * One review per order item; an existing review is edited instead of duplicated (MSG-035).
 */
export default function OrderItemReview({ orderItemId, productName }: { orderItemId: number; productName: string }) {
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [editing, setEditing] = useState(false)
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let active = true
    getMyReview(orderItemId)
      .then((review) => { if (active) setState({ kind: 'ready', review }) })
      .catch((failure: unknown) => { if (active) setState({ kind: 'failed', message: getUiError(failure) }) })
    return () => { active = false }
  }, [orderItemId])

  if (state.kind === 'loading') return null
  if (state.kind === 'failed') return <p className="rv-order-error" role="alert">{state.message}</p>
  const review = state.review

  function open() {
    setRating(review?.rating ?? 0); setComment(review?.comment ?? '')
    setFormError(''); setNotice(''); setEditing(true)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (rating < 1) { setFormError('Hãy chọn số sao trước khi gửi.'); return }
    const input = { rating, comment: comment.trim() || null }
    setBusy(true); setFormError('')
    try {
      const saved = review ? await updateReview(orderItemId, input) : await createReview(orderItemId, input)
      setState({ kind: 'ready', review: saved }); setEditing(false)
      setNotice(review ? 'Đã cập nhật đánh giá.' : 'Cảm ơn bạn đã đánh giá sản phẩm!')
    } catch (failure) {
      // Reviewed in another tab: load that review and let the customer edit it instead.
      if (isAxiosError(failure) && (failure.response?.data as { code?: string } | undefined)?.code === 'REVIEW_ALREADY_EXISTS') {
        const existing = await getMyReview(orderItemId).catch(() => null)
        if (existing) setState({ kind: 'ready', review: existing })
      }
      setFormError(getUiError(failure))
    } finally { setBusy(false) }
  }

  if (editing) return <form className="rv-order rv-order-form" onSubmit={submit}
    aria-label={`${review ? 'Sửa đánh giá' : 'Đánh giá'} ${productName}`}>
    <StarPicker name={`rating-${orderItemId}`} value={rating} onChange={setRating} disabled={busy} />
    <label className="rv-order-label">Nhận xét (tùy chọn)
      <textarea maxLength={2000} value={comment} disabled={busy} placeholder="Chất liệu, kích cỡ, màu sắc…"
        onChange={(event) => setComment(event.target.value)} /></label>
    {formError && <p className="rv-order-error" role="alert">{formError}</p>}
    <div className="rv-order-actions">
      <button type="button" className="shop-button ghost small" disabled={busy} onClick={() => setEditing(false)}>Hủy</button>
      <button type="submit" className="shop-button small" disabled={busy}>
        {busy ? 'Đang gửi…' : review ? 'Lưu đánh giá' : 'Gửi đánh giá'}</button>
    </div>
  </form>

  if (!review) return <div className="rv-order">
    <button type="button" className="shop-button ghost small" onClick={open}
      aria-label={`Đánh giá ${productName}`}>★ Đánh giá sản phẩm</button>
    {notice && <p className="rv-order-notice" role="status">{notice}</p>}
  </div>

  return <div className="rv-order rv-order-done">
    <div className="rv-order-head">
      <Stars rating={review.rating} />
      <span>Đánh giá của bạn</span>
      <button type="button" className="link-button" onClick={open} aria-label={`Sửa đánh giá ${productName}`}>Sửa</button>
    </div>
    {review.comment && <p className="rv-order-comment">{review.comment}</p>}
    {review.status === 'HIDDEN' && <p className="rv-order-hidden">Đánh giá này đang được cửa hàng ẩn khỏi trang sản phẩm.</p>}
    {review.managerReply && <div className="rv-reply"><b>Phản hồi từ FORME</b><p>{review.managerReply}</p></div>}
    {notice && <p className="rv-order-notice" role="status">{notice}</p>}
  </div>
}
