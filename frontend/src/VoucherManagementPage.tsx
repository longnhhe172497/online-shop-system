import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { createVoucher, deleteVoucher, listVouchers, updateVoucher, type Voucher, type VoucherInput } from './api/vouchers'
import { formatDateTime, formatVnd } from './orderFormat'
import { getUiError } from './uiError'
import './orders.css'

const statusNames: Record<Voucher['status'], string> = { DRAFT: 'Nháp', ACTIVE: 'Đang chạy', INACTIVE: 'Tạm dừng', EXPIRED: 'Hết hạn' }

/** datetime-local works in local time without a zone; the API needs an ISO offset. */
function toLocalInput(iso: string) {
  const date = new Date(iso)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
function fromLocalInput(value: string) {
  return new Date(value).toISOString()
}

type Form = Omit<VoucherInput, 'discountValue' | 'maxDiscount' | 'minOrderAmount' | 'usageLimit'> & {
  discountValue: string; maxDiscount: string; minOrderAmount: string; usageLimit: string }

function emptyForm(): Form {
  const now = new Date()
  return { code: '', discountType: 'FIXED', discountValue: '', maxDiscount: '', minOrderAmount: '0', usageLimit: '100',
    validFrom: toLocalInput(now.toISOString()), validUntil: toLocalInput(new Date(now.getTime() + 30 * 86400000).toISOString()),
    status: 'ACTIVE' }
}

function describe(voucher: Voucher) {
  const value = voucher.discountType === 'PERCENT' ? `${voucher.discountValue}%` : formatVnd(voucher.discountValue)
  const cap = voucher.discountType === 'PERCENT' && voucher.maxDiscount ? `Tối đa ${formatVnd(voucher.maxDiscount)} · ` : ''
  return { main: `Giảm ${value}`, sub: `${cap}Đơn từ ${formatVnd(voucher.minOrderAmount)}` }
}

function shortDate(value: string) {
  return new Date(value).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: '2-digit' })
}

export default function VoucherManagementPage() {
  const [vouchers, setVouchers] = useState<Voucher[]>([])
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [editing, setEditing] = useState<{ id: number | null; form: Form } | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Voucher | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    try { const data = await listVouchers(search.trim(), page); setVouchers(data.items); setTotalPages(data.totalPages); setError('') }
    catch (failure) { setError(getUiError(failure)) }
  }, [search, page])
  useEffect(() => { const timer = window.setTimeout(() => { void load() }, 250); return () => window.clearTimeout(timer) }, [load])

  function edit(voucher: Voucher) {
    setFormError('')
    setEditing({ id: voucher.id, form: { code: voucher.code, discountType: voucher.discountType,
      discountValue: String(voucher.discountValue), maxDiscount: voucher.maxDiscount == null ? '' : String(voucher.maxDiscount),
      minOrderAmount: String(voucher.minOrderAmount), usageLimit: String(voucher.usageLimit),
      validFrom: toLocalInput(voucher.validFrom), validUntil: toLocalInput(voucher.validUntil),
      status: voucher.status === 'EXPIRED' ? 'INACTIVE' : voucher.status } })
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editing) return
    const { form } = editing
    const input: VoucherInput = { code: form.code.trim(), discountType: form.discountType,
      discountValue: Number(form.discountValue), maxDiscount: form.maxDiscount ? Number(form.maxDiscount) : null,
      minOrderAmount: Number(form.minOrderAmount || 0), usageLimit: Number(form.usageLimit),
      validFrom: fromLocalInput(form.validFrom), validUntil: fromLocalInput(form.validUntil), status: form.status }
    setBusy(true); setFormError('')
    try {
      if (editing.id) await updateVoucher(editing.id, input)
      else await createVoucher(input)
      setEditing(null); setNotice(editing.id ? 'Đã cập nhật mã giảm giá.' : 'Đã tạo mã giảm giá.'); await load()
    } catch (failure) { setFormError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function remove(voucher: Voucher) {
    setBusy(true); setError(''); setNotice('')
    try { await deleteVoucher(voucher.id); setNotice(`Đã xóa mã ${voucher.code}.`); await load() }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false); setPendingDelete(null) }
  }

  const set = (patch: Partial<Form>) => setEditing((current) => current && { ...current, form: { ...current.form, ...patch } })

  return <section className="admin-panel">
    <div className="admin-section-head"><div>
      <p>Mã áp dụng cho toàn cửa hàng. Lượt dùng tính cả đơn đang chờ thanh toán.</p></div>
      <button type="button" className="account-primary" onClick={() => { setFormError(''); setEditing({ id: null, form: emptyForm() }) }}>
        + Tạo mã mới</button></div>
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}
    <div className="voucher-stats">
      <div><span>Đang chạy</span><strong>{vouchers.filter((voucher) => voucher.status === 'ACTIVE').length}</strong></div>
      <div><span>Sắp hết lượt (≥ 80%)</span><strong>{vouchers.filter((voucher) =>
        voucher.usedCount >= voucher.usageLimit * 0.8).length}</strong></div>
      <div><span>Tổng lượt đã dùng</span><strong>{vouchers.reduce((sum, voucher) => sum + voucher.usedCount, 0)}</strong></div>
    </div>
    <div className="admin-filters"><input aria-label="Tìm mã giảm giá" placeholder="Tìm theo mã…" value={search}
      onChange={(event) => { setSearch(event.target.value); setPage(0) }} /></div>
    {vouchers.length === 0 && !error && <p className="admin-empty">Chưa có mã giảm giá nào.</p>}
    {vouchers.length > 0 && <div className="admin-table-wrap"><table className="admin-table voucher-table">
      <thead><tr><th>Mã</th><th>Ưu đãi</th><th>Lượt dùng</th><th>Hiệu lực</th><th></th></tr></thead>
      <tbody>{vouchers.map((voucher) => <tr key={voucher.id}>
        <td><span className="voucher-code">{voucher.code}</span>
          <span className={`status-badge ${voucher.status === 'ACTIVE' ? 'success' : 'neutral'}`}>{statusNames[voucher.status]}</span></td>
        <td><strong>{describe(voucher).main}</strong><small className="cell-sub">{describe(voucher).sub}</small></td>
        <td><div className={`usage-meter ${voucher.usedCount >= voucher.usageLimit ? 'full' : ''}`}>
          <span>{voucher.usedCount}/{voucher.usageLimit} lượt</span>
          <div><i style={{ width: `${Math.min(100, (voucher.usedCount / voucher.usageLimit) * 100)}%` }} /></div></div></td>
        <td title={`${formatDateTime(voucher.validFrom)} → ${formatDateTime(voucher.validUntil)}`}>
          {shortDate(voucher.validFrom)} → {shortDate(voucher.validUntil)}</td>
        <td><div className="row-actions"><button type="button" className="account-secondary" onClick={() => edit(voucher)}>Sửa</button>
          <button type="button" className="account-secondary" onClick={() => setPendingDelete(voucher)}>Xóa</button></div></td>
      </tr>)}</tbody>
    </table></div>}
    {totalPages > 1 && <div className="admin-pagination">
      <button type="button" className="account-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
      <span>Trang {page + 1}/{totalPages}</span>
      <button type="button" className="account-secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
    </div>}

    {editing && <div className="address-modal-backdrop"><section className="address-modal admin-editor" role="dialog"
      aria-modal="true" aria-labelledby="voucher-editor-title">
      <h2 id="voucher-editor-title">{editing.id ? 'Sửa mã giảm giá' : 'Tạo mã giảm giá'}</h2>
      {formError && <p className="auth-error" role="alert">{formError}</p>}
      <form className="voucher-form" onSubmit={save}>
        <label>Mã<input required value={editing.form.code} pattern="[A-Za-z0-9_\-]{3,60}"
          title="3–60 ký tự: chữ, số, gạch dưới hoặc gạch ngang" onChange={(event) => set({ code: event.target.value })} /></label>
        <label>Loại giảm<select value={editing.form.discountType}
          onChange={(event) => set({ discountType: event.target.value as Form['discountType'] })}>
          <option value="FIXED">Số tiền cố định</option><option value="PERCENT">Phần trăm</option></select></label>
        <label>{editing.form.discountType === 'PERCENT' ? 'Phần trăm giảm (%)' : 'Số tiền giảm (₫)'}
          <input required type="number" min="1" max={editing.form.discountType === 'PERCENT' ? 100 : undefined}
            value={editing.form.discountValue} onChange={(event) => set({ discountValue: event.target.value })} /></label>
        <label>Giảm tối đa (₫, tùy chọn)<input type="number" min="1" value={editing.form.maxDiscount}
          onChange={(event) => set({ maxDiscount: event.target.value })} /></label>
        <label>Đơn tối thiểu (₫)<input required type="number" min="0" value={editing.form.minOrderAmount}
          onChange={(event) => set({ minOrderAmount: event.target.value })} /></label>
        <label>Giới hạn lượt dùng<input required type="number" min="1" value={editing.form.usageLimit}
          onChange={(event) => set({ usageLimit: event.target.value })} /></label>
        <label>Bắt đầu<input required type="datetime-local" value={editing.form.validFrom}
          onChange={(event) => set({ validFrom: event.target.value })} /></label>
        <label>Kết thúc<input required type="datetime-local" value={editing.form.validUntil}
          onChange={(event) => set({ validUntil: event.target.value })} /></label>
        <label>Trạng thái<select value={editing.form.status} onChange={(event) => set({ status: event.target.value as Form['status'] })}>
          <option value="ACTIVE">Đang chạy</option><option value="DRAFT">Nháp</option><option value="INACTIVE">Tạm dừng</option></select></label>
        <div className="address-modal-actions form-span-2"><button type="button" className="account-secondary" disabled={busy}
          onClick={() => setEditing(null)}>Hủy</button>
          <button type="submit" className="account-primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu'}</button></div>
      </form>
    </section></div>}

    {pendingDelete && <div className="address-modal-backdrop"><section className="address-modal confirm-modal"
      role="dialog" aria-modal="true" aria-labelledby="delete-voucher-title">
      <p className="card-eyebrow">XÁC NHẬN</p><h2 id="delete-voucher-title">Xóa mã {pendingDelete.code}?</h2>
      <p>Chỉ xóa được mã chưa từng dùng. Mã đã dùng hãy chuyển sang Tạm dừng.</p>
      <div className="address-modal-actions"><button type="button" className="account-secondary" disabled={busy}
        onClick={() => setPendingDelete(null)}>Không xóa</button>
        <button type="button" className="account-primary" disabled={busy} onClick={() => void remove(pendingDelete)}>Xóa</button></div>
    </section></div>}
  </section>
}
