import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { changeProductStatus, createProduct, listCategoryOptions, listManagerProducts, updateProduct,
  type CategoryOption, type ManagerProduct, type ProductInput, type ProductStatus } from './api/managerCatalog'
import ProductImage from './ProductImage'
import { formatVnd } from './formatMoney'
import { getUiError } from './uiError'
import './orders.css'
import './logistics.css'
import './managerCatalog.css'

const productStatusNames: Record<ProductStatus, string> = {
  DRAFT: 'Nháp', ACTIVE: 'Đang bán', INACTIVE: 'Tạm ẩn', ARCHIVED: 'Lưu trữ' }
const statusTone: Record<ProductStatus, string> = { DRAFT: 'lg-info', ACTIVE: 'lg-ok', INACTIVE: 'lg-warn', ARCHIVED: 'lg-muted' }
const statuses = Object.keys(productStatusNames) as ProductStatus[]

type Form = { sku: string; name: string; description: string; imageUrl: string; price: string; categoryId: string }
const emptyForm: Form = { sku: '', name: '', description: '', imageUrl: '', price: '', categoryId: '' }

function toInput(form: Form): ProductInput {
  return { sku: form.sku.trim(), name: form.name.trim(), description: form.description.trim() || null,
    imageUrl: form.imageUrl.trim() || null, price: Number(form.price), categoryId: Number(form.categoryId) }
}

export default function ManagerProductsPage() {
  const [products, setProducts] = useState<ManagerProduct[]>([])
  const [loaded, setLoaded] = useState(false)
  const [totalItems, setTotalItems] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<'' | ProductStatus>('')
  const [categories, setCategories] = useState<CategoryOption[]>([])
  const [editing, setEditing] = useState<{ product: ManagerProduct | null; form: Form } | null>(null)
  const [pendingArchive, setPendingArchive] = useState<ManagerProduct | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let active = true
    listCategoryOptions().then((options) => { if (active) setCategories(options) }).catch(() => {})
    return () => { active = false }
  }, [])

  const load = useCallback(async () => {
    try {
      const data = await listManagerProducts(page, { q: search.trim() || undefined,
        categoryId: categoryFilter ? Number(categoryFilter) : undefined, status: statusFilter || undefined })
      setProducts(data.items); setTotalItems(data.totalItems); setTotalPages(data.totalPages); setError('')
    } catch (failure) { setError(getUiError(failure)) }
    finally { setLoaded(true) }
  }, [page, search, categoryFilter, statusFilter])
  useEffect(() => { const timer = window.setTimeout(() => { void load() }, 250); return () => window.clearTimeout(timer) }, [load])

  function openCreate() {
    setFormError('')
    setEditing({ product: null, form: { ...emptyForm, categoryId: categories.find((c) => c.status === 'ACTIVE')?.id.toString() ?? '' } })
  }

  function openEdit(product: ManagerProduct) {
    setFormError('')
    setEditing({ product, form: { sku: product.sku, name: product.name, description: product.description ?? '',
      imageUrl: product.imageUrl ?? '', price: String(product.price), categoryId: String(product.categoryId) } })
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editing) return
    setBusy(true); setFormError('')
    try {
      const input = toInput(editing.form)
      if (editing.product) await updateProduct(editing.product.id, input)
      else await createProduct(input)
      setNotice(editing.product ? `Đã cập nhật ${input.name}.` : `Đã tạo ${input.name} ở trạng thái Nháp.`)
      setEditing(null); await load()
    } catch (failure) { setFormError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  async function setStatus(product: ManagerProduct, status: ProductStatus) {
    if (status === product.status) return
    if (status === 'ARCHIVED' && pendingArchive?.id !== product.id) { setPendingArchive(product); return }
    setBusy(true); setError(''); setNotice('')
    try {
      await changeProductStatus(product.id, status)
      setNotice(`${product.name}: ${productStatusNames[status]}.`); await load()
    } catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false); setPendingArchive(null) }
  }

  const set = (patch: Partial<Form>) => setEditing((current) => current && { ...current, form: { ...current.form, ...patch } })
  const currentCategory = editing?.product && !categories.some((c) => c.id === editing.product?.categoryId)
    ? { id: editing.product.categoryId, name: editing.product.categoryName, status: editing.product.categoryStatus } : null

  return <section className="admin-panel">
    <div className="admin-section-head"><div>
      <p>Sản phẩm mới luôn bắt đầu ở trạng thái Nháp. Tồn kho do bộ phận kho cập nhật.</p></div>
      <button type="button" className="account-primary" onClick={openCreate}>+ Thêm sản phẩm</button></div>
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}

    <div className="admin-filters catalog-filters">
      <input aria-label="Tìm sản phẩm" placeholder="Tìm theo tên hoặc SKU…" value={search} maxLength={100}
        onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
      <select aria-label="Lọc theo danh mục" value={categoryFilter}
        onChange={(event) => { setCategoryFilter(event.target.value); setPage(0) }}>
        <option value="">Tất cả danh mục</option>
        {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
      </select>
      <select aria-label="Lọc theo trạng thái" value={statusFilter}
        onChange={(event) => { setStatusFilter(event.target.value as '' | ProductStatus); setPage(0) }}>
        <option value="">Mọi trạng thái</option>
        {statuses.map((status) => <option key={status} value={status}>{productStatusNames[status]}</option>)}
      </select>
    </div>

    {loaded && products.length === 0 && !error && <p className="admin-empty">Không có sản phẩm phù hợp.</p>}
    {products.length > 0 && <div className="lg-tablewrap catalog-tablewrap"><table className="lg-table catalog-table">
      <thead><tr><th>Sản phẩm</th><th>Danh mục</th><th>Giá</th><th>Tồn kho</th><th>Trạng thái</th><th></th></tr></thead>
      <tbody>{products.map((product) => <tr key={product.id}>
        <td><div className="catalog-product"><div className="catalog-thumb">
          <ProductImage src={product.imageUrl} alt={product.name} /></div>
          <div><strong>{product.name}</strong><span className="catalog-sku">{product.sku}</span></div></div></td>
        <td>{product.categoryName}
          {product.categoryStatus !== 'ACTIVE' && <small className="cell-sub">Danh mục đang ẩn</small>}</td>
        <td>{formatVnd(product.price)}</td>
        <td className="catalog-stock">{product.availableQuantity} có thể bán
          <small className="cell-sub">{product.stockOnHand} trong kho · {product.stockReserved} đang giữ</small></td>
        <td className="catalog-status">
          <span className={`lg-chip ${statusTone[product.status]}`}>{productStatusNames[product.status]}</span>
          <select aria-label={`Đổi trạng thái ${product.name}`} value={product.status} disabled={busy}
            onChange={(event) => void setStatus(product, event.target.value as ProductStatus)}>
            {statuses.map((status) => <option key={status} value={status}>{productStatusNames[status]}</option>)}
          </select></td>
        <td><div className="row-actions">
          <button type="button" className="lg-btn ghost" onClick={() => openEdit(product)}
            aria-label={`Sửa ${product.name}`}>Sửa</button></div></td>
      </tr>)}</tbody>
    </table></div>}
    {totalPages > 1 && <div className="admin-pagination">
      <button type="button" className="account-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
      <span>Trang {page + 1}/{totalPages} · {totalItems} sản phẩm</span>
      <button type="button" className="account-secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
    </div>}

    {editing && <div className="address-modal-backdrop"><section className="address-modal admin-editor" role="dialog"
      aria-modal="true" aria-labelledby="product-editor-title">
      <h2 id="product-editor-title">{editing.product ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}</h2>
      {formError && <p className="auth-error" role="alert">{formError}</p>}
      <form className="catalog-form" onSubmit={save}>
        <label>Mã SKU<input required maxLength={80} pattern="\s*[A-Za-z0-9][A-Za-z0-9_\-]*\s*" value={editing.form.sku}
          title="Chữ, số, gạch dưới hoặc gạch ngang" onChange={(event) => set({ sku: event.target.value })} /></label>
        <label>Tên sản phẩm<input required maxLength={200} value={editing.form.name}
          onChange={(event) => set({ name: event.target.value })} /></label>
        <label>Giá bán (₫)<input required type="number" min="1" step="any" value={editing.form.price}
          onChange={(event) => set({ price: event.target.value })} /></label>
        <label>Danh mục<select required value={editing.form.categoryId} onChange={(event) => set({ categoryId: event.target.value })}>
          <option value="" disabled>Chọn danh mục</option>
          {currentCategory && <option value={currentCategory.id}>{currentCategory.name}</option>}
          {categories.map((category) => <option key={category.id} value={category.id}>
            {category.name}{category.status === 'ACTIVE' ? '' : ' (đang ẩn)'}</option>)}
        </select></label>
        <label className="form-span-2">Đường dẫn ảnh (tùy chọn)<input maxLength={500} pattern="\s*(https?://|/)\S+\s*"
          title="Bắt đầu bằng http://, https:// hoặc /" placeholder="https://… hoặc /uploads/…" value={editing.form.imageUrl}
          onChange={(event) => set({ imageUrl: event.target.value })} /></label>
        <label className="form-span-2">Mô tả (tùy chọn)<textarea maxLength={5000} value={editing.form.description}
          onChange={(event) => set({ description: event.target.value })} /></label>
        <p className="catalog-readonly form-span-2">{editing.product
          ? `Tồn kho: ${editing.product.stockOnHand} · Đang giữ: ${editing.product.stockReserved} · Có thể bán: ${editing.product.availableQuantity}. Bộ phận kho cập nhật số lượng này.`
          : 'Sản phẩm mới được tạo ở trạng thái Nháp với tồn kho 0. Mở bán sau khi kiểm tra thông tin.'}</p>
        <div className="address-modal-actions form-span-2"><button type="button" className="account-secondary" disabled={busy}
          onClick={() => setEditing(null)}>Hủy</button>
          <button type="submit" className="account-primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu'}</button></div>
      </form>
    </section></div>}

    {pendingArchive && <div className="address-modal-backdrop"><section className="address-modal confirm-modal"
      role="dialog" aria-modal="true" aria-labelledby="archive-product-title">
      <p className="card-eyebrow">XÁC NHẬN</p><h2 id="archive-product-title">Lưu trữ {pendingArchive.name}?</h2>
      <p>Sản phẩm sẽ ngừng hiển thị trên cửa hàng. Lịch sử đơn hàng vẫn được giữ nguyên.</p>
      <div className="address-modal-actions"><button type="button" className="account-secondary" disabled={busy}
        onClick={() => setPendingArchive(null)}>Không lưu trữ</button>
        <button type="button" className="account-primary" disabled={busy}
          onClick={() => void setStatus(pendingArchive, 'ARCHIVED')}>Lưu trữ</button></div>
    </section></div>}
  </section>
}
