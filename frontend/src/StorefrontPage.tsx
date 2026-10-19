import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { listProducts, type Page, type Product, type ProductQuery, type ProductSort } from './api/products'
import { listCategories, type Category } from './api/catalog'
import StoreHeader from './StoreHeader'
import ProductImage from './ProductImage'
import { formatVnd } from './formatMoney'

const PAGE_SIZE = 12

const SORTS: { value: ProductSort; label: string }[] = [
  { value: 'newest', label: 'Mới nhất' },
  { value: 'price_asc', label: 'Giá: thấp đến cao' },
  { value: 'price_desc', label: 'Giá: cao đến thấp' },
  { value: 'name', label: 'Tên: A đến Z' },
]

const PRICES: { value: string; label: string; min?: number; max?: number }[] = [
  { value: '', label: 'Mọi mức giá' },
  { value: 'u500', label: 'Dưới 500.000 ₫', max: 500000 },
  { value: '500-1000', label: '500.000 – 1.000.000 ₫', min: 500000, max: 1000000 },
  { value: '1000-2000', label: '1.000.000 – 2.000.000 ₫', min: 1000000, max: 2000000 },
  { value: 'o2000', label: 'Trên 2.000.000 ₫', min: 2000000 },
]

interface Loaded {
  key: string
  page?: Page<Product>
  failed?: boolean
}

export default function StorefrontPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const categoryId = Number(params.get('categoryId')) || undefined
  const price = PRICES.find((option) => option.value === (params.get('price') ?? '')) ?? PRICES[0]
  const sortParam = params.get('sort')
  const sort: ProductSort = SORTS.some((option) => option.value === sortParam) ? sortParam as ProductSort : 'newest'
  const page = Math.max(0, Math.floor(Number(params.get('page'))) || 0)
  const queryKey = JSON.stringify([q, categoryId, price.value, sort, page])

  const [categories, setCategories] = useState<Category[]>([])
  const [loaded, setLoaded] = useState<Loaded | null>(null)

  useEffect(() => {
    let active = true
    listCategories().then((list) => { if (active) setCategories(list) }).catch(() => {})
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    const query: ProductQuery = { q: q || undefined, categoryId, minPrice: price.min, maxPrice: price.max, sort }
    listProducts(page, PAGE_SIZE, query)
      .then((result) => { if (active) setLoaded({ key: queryKey, page: result }) })
      .catch(() => { if (active) setLoaded({ key: queryKey, failed: true }) })
    return () => { active = false }
  }, [q, categoryId, price, sort, page, queryKey])

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  const current = loaded?.key === queryKey ? loaded : null
  const result = current?.page
  const filtered = Boolean(q || categoryId || price.value)

  return <div className="forme-page">
    <StoreHeader />

    <section className="shop-hero">
      <p className="shop-kicker">BỘ SƯU TẬP MỚI</p>
      <h1>Mặc đẹp<br />mỗi ngày.</h1>
      <p>Thiết kế tối giản, chất liệu dễ chịu, phù hợp cho mọi hoạt động thường ngày.</p>
      <a className="shop-primary" href="#san-pham">MUA SẮM NGAY</a>
    </section>

    <main className="shop-main" id="san-pham">
      <div className="shop-pagehead">
        <div>
          <p className="shop-kicker">BỘ SƯU TẬP</p>
          <h2>Sản phẩm</h2>
        </div>
        {result && <span>{result.totalItems} sản phẩm</span>}
      </div>

      <div className="shop-filters">
        <form key={q} role="search" onSubmit={(event) => {
          event.preventDefault()
          update({ q: String(new FormData(event.currentTarget).get('q') ?? '').trim() })
        }}>
          <input type="search" name="q" aria-label="Tìm sản phẩm" placeholder="Tìm theo tên, mô tả, danh mục"
            defaultValue={q} maxLength={100} />
          <button className="shop-secondary" type="submit">Tìm</button>
        </form>
        <select aria-label="Danh mục" value={categoryId ? String(categoryId) : ''}
          onChange={(event) => update({ categoryId: event.target.value })}>
          <option value="">Tất cả danh mục</option>
          {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </select>
        <select aria-label="Mức giá" value={price.value} onChange={(event) => update({ price: event.target.value })}>
          {PRICES.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <select aria-label="Sắp xếp" value={sort}
          onChange={(event) => update({ sort: event.target.value === 'newest' ? '' : event.target.value })}>
          {SORTS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </div>

      {!current && <p className="shop-state">Đang tải sản phẩm…</p>}
      {current?.failed && <p className="shop-state" role="alert">
        Không thể tải sản phẩm. Hãy kiểm tra backend và PostgreSQL đang chạy.</p>}

      {result && result.items.length === 0 && <div className="shop-empty">
        <h3>{filtered ? 'Không tìm thấy sản phẩm phù hợp.' : 'Chưa có sản phẩm đang bán.'}</h3>
        {filtered && <button className="shop-secondary" type="button"
          onClick={() => update({ q: '', categoryId: '', price: '' })}>Xóa bộ lọc</button>}
      </div>}

      {result && result.items.length > 0 && <ul className="shop-grid">
        {result.items.map((product) => <li key={product.id}>
          <Link className="shop-card" to={`/products/${product.id}`}>
            <ProductImage src={product.imageUrl} alt={product.name} />
            {product.availableQuantity <= 0 && <span className="shop-badge">HẾT HÀNG</span>}
            <div className="shop-card-meta">
              <div><strong>{product.name}</strong><small>{product.categoryName}</small></div>
              <b>{formatVnd(product.price)}</b>
            </div>
          </Link>
        </li>)}
      </ul>}

      {result && result.totalPages > 1 && <nav className="shop-pager" aria-label="Phân trang">
        <button className="shop-secondary" type="button" disabled={page === 0}
          onClick={() => update({ page: String(page - 1) })}>Trang trước</button>
        <span>Trang {page + 1} / {result.totalPages}</span>
        <button className="shop-secondary" type="button" disabled={page + 1 >= result.totalPages}
          onClick={() => update({ page: String(page + 1) })}>Trang sau</button>
      </nav>}
    </main>

    <footer className="forme-footer">
      <strong>FORME</strong>
      <span>Thời trang tối giản cho mỗi ngày.</span>
    </footer>
  </div>
}