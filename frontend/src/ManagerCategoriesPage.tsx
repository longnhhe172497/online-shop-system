import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { changeCategoryStatus, createCategory, deleteCategory, listManagerCategories, updateCategory,
  type CategoryStatus, type ManagerCategory } from './api/managerCatalog'
import { getUiError } from './uiError'
import './orders.css'
import './logistics.css'
import './managerCatalog.css'

const statusNames: Record<CategoryStatus, string> = { ACTIVE: 'Đang hiển thị', INACTIVE: 'Tạm ẩn', ARCHIVED: 'Lưu trữ' }
const statusTone: Record<CategoryStatus, string> = { ACTIVE: 'lg-ok', INACTIVE: 'lg-warn', ARCHIVED: 'lg-muted' }
const statuses = Object.keys(statusNames) as CategoryStatus[]

type Form = { name: string; description: string }
type Pending = { category: ManagerCategory; action: 'archive' | 'delete' }

export default function ManagerCategoriesPage() {
  const [categories, setCategories] = useState<ManagerCategory[]>([])
  const [loaded, setLoaded] = useState(false)
  const [statusFilter, setStatusFilter] = useState<'' | CategoryStatus>('')
  const [editing, setEditing] = useState<{ category: ManagerCategory | null; form: Form } | null>(null)
  const [pending, setPending] = useState<Pending | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    try { setCategories(await listManagerCategories(statusFilter || undefined)); setError('') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setLoaded(true) }
  }, [statusFilter])
  useEffect(() => { const timer = window.setTimeout(() => { void load() }, 0); return () => window.clearTimeout(timer) }, [load])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editing) return
    const input = { name: editing.form.name.trim(), description: editing.form.description.trim() || null }
    setBusy(true); setFormError('')
    try {
      if (editing.category) await updateCategory(editing.category.id, input)
      else await createCategory(input)
      setNotice(editing.category ? `Đã cập nhật danh mục ${input.name}.` : `Đã tạo danh mục ${input.name}.`)
      setEditing(null); await load()
    } catch (failure) { setFormError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function run(action: () => Promise<unknown>, message: string) {
    setBusy(true); setError(''); setNotice('')
    try { await action(); setNotice(message); await load() }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false); setPending(null) }
  }

  function setStatus(category: ManagerCategory, status: CategoryStatus) {
    if (status === category.status) return
    if (status === 'ARCHIVED') { setPending({ category, action: 'archive' }); return }
    void run(() => changeCategoryStatus(category.id, status), `${category.name}: ${statusNames[status]}.`)
  }

  const set = (patch: Partial<Form>) => setEditing((current) => current && { ...current, form: { ...current.form, ...patch } })

  return <section className="admin-panel">
    <div className="admin-section-head"><div>
      <p>Danh mục tạm ẩn sẽ ẩn luôn các sản phẩm bên trong khỏi cửa hàng.</p></div>
      <button type="button" className="account-primary" onClick={() => { setFormError('')
        setEditing({ category: null, form: { name: '', description: '' } }) }}>+ Thêm danh mục</button></div>
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}

    <div className="admin-filters"><select aria-label="Lọc theo trạng thái" value={statusFilter}
      onChange={(event) => setStatusFilter(event.target.value as '' | CategoryStatus)}>
      <option value="">Mọi trạng thái</option>
      {statuses.map((status) => <option key={status} value={status}>{statusNames[status]}</option>)}
    </select></div>

    {loaded && categories.length === 0 && !error && <p className="admin-empty">Chưa có danh mục nào.</p>}
    {categories.length > 0 && <div className="lg-tablewrap catalog-tablewrap"><table className="lg-table catalog-table">
      <thead><tr><th>Danh mục</th><th>Sản phẩm</th><th>Trạng thái</th><th></th></tr></thead>
      <tbody>{categories.map((category) => <tr key={category.id}>
        <td><strong>{category.name}</strong>{category.description && <small className="cell-sub">{category.description}</small>}</td>
        <td>{category.activeProductCount} đang bán<small className="cell-sub">{category.productCount} tổng cộng</small></td>
        <td className="catalog-status">
          <span className={`lg-chip ${statusTone[category.status]}`}>{statusNames[category.status]}</span>
          <select aria-label={`Đổi trạng thái ${category.name}`} value={category.status} disabled={busy}
            onChange={(event) => setStatus(category, event.target.value as CategoryStatus)}>
            {statuses.map((status) => <option key={status} value={status}>{statusNames[status]}</option>)}
          </select></td>
        <td><div className="row-actions">
          <button type="button" className="lg-btn ghost" aria-label={`Sửa ${category.name}`} onClick={() => {
            setFormError(''); setEditing({ category, form: { name: category.name, description: category.description ?? '' } }) }}>Sửa</button>
          <button type="button" className="lg-btn danger" aria-label={`Xóa ${category.name}`}
            disabled={busy || category.productCount > 0}
            title={category.productCount > 0 ? 'Chỉ xóa được danh mục không còn sản phẩm' : undefined}
            onClick={() => setPending({ category, action: 'delete' })}>Xóa</button></div></td>
      </tr>)}</tbody>
    </table></div>}

    {editing && <div className="address-modal-backdrop"><section className="address-modal admin-editor" role="dialog"
      aria-modal="true" aria-labelledby="category-editor-title">
      <h2 id="category-editor-title">{editing.category ? 'Sửa danh mục' : 'Thêm danh mục'}</h2>
      {formError && <p className="auth-error" role="alert">{formError}</p>}
      <form className="catalog-form" onSubmit={save}>
        <label className="form-span-2">Tên danh mục<input required maxLength={150} value={editing.form.name}
          onChange={(event) => set({ name: event.target.value })} /></label>
        <label className="form-span-2">Mô tả (tùy chọn)<textarea maxLength={2000} value={editing.form.description}
          onChange={(event) => set({ description: event.target.value })} /></label>
        <div className="address-modal-actions form-span-2"><button type="button" className="account-secondary" disabled={busy}
          onClick={() => setEditing(null)}>Hủy</button>
          <button type="submit" className="account-primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu'}</button></div>
      </form>
    </section></div>}

    {pending && <div className="address-modal-backdrop"><section className="address-modal confirm-modal"
      role="dialog" aria-modal="true" aria-labelledby="category-confirm-title">
      <p className="card-eyebrow">XÁC NHẬN</p>
      <h2 id="category-confirm-title">{pending.action === 'delete' ? 'Xóa' : 'Lưu trữ'} danh mục {pending.category.name}?</h2>
      <p>{pending.action === 'delete' ? 'Danh mục sẽ bị xóa vĩnh viễn.'
        : 'Chỉ lưu trữ được khi mọi sản phẩm trong danh mục đã được lưu trữ hoặc chuyển sang danh mục khác.'}</p>
      <div className="address-modal-actions"><button type="button" className="account-secondary" disabled={busy}
        onClick={() => setPending(null)}>Hủy</button>
        <button type="button" className="account-primary" disabled={busy} onClick={() => void (pending.action === 'delete'
          ? run(() => deleteCategory(pending.category.id), `Đã xóa danh mục ${pending.category.name}.`)
          : run(() => changeCategoryStatus(pending.category.id, 'ARCHIVED'), `${pending.category.name}: Lưu trữ.`))}>
          {pending.action === 'delete' ? 'Xóa' : 'Lưu trữ'}</button></div>
    </section></div>}
  </section>
}
