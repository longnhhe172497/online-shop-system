import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { listProducts, type Page, type Product, type ProductQuery, type ProductSort } from './api/products'
import { listCategories, type Category } from './api/catalog'
import StoreLayout from './StoreLayout'
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

  return <StoreLayout>
    <section className="fs-hero">
      <div className="fs-hero-content">
        <p className="fs-eyebrow">Bộ sưu tập mới</p>
        <h1>Khoác lên<br /><em>trọn sắc màu.</em></h1>
        <p>Những thiết kế tối giản với màu sắc nổi bật, chất liệu dễ chịu cho mọi hoạt động thường ngày.</p>
        <a className="fs-btn orange" href="#san-pham">Xem bộ sưu tập →</a>
      </div>
      {/* Free Unsplash photos; see public/images/hero/CREDITS.md */}
      <div className="fs-hero-gallery" aria-hidden="true">
        <img className="hero-main" src="/images/hero/hero-white-suit.jpg" alt="" width="900" height="1111" />
        <img className="hero-top" src="/images/hero/hero-blazer.jpg" alt="" width="620" height="775" />
        <img className="hero-bottom" src="/images/hero/hero-satin-dress.jpg" alt="" width="620" height="1102" />
        <span className="hero-credit">Ảnh: Unsplash</span>
      </div>
    </section>

    <main className="fs-shell" id="san-pham">
      <div className="fs-pagehead">
        <div>
          <p className="fs-eyebrow">Bộ sưu tập</p>
          <h2>Sản phẩm</h2>
        </div>
        {result && <span className="fs-muted">{result.totalItems} sản phẩm</span>}
      </div>

      <div className="fs-filters">
        <form key={q} className="fs-search" role="search" onSubmit={(event) => {
          event.preventDefault()
          update({ q: String(new FormData(event.currentTarget).get('q') ?? '').trim() })
        }}>
          <input className="fs-field" type="search" name="q" aria-label="Tìm sản phẩm"
            placeholder="Tìm theo tên, mô tả, danh mục" defaultValue={q} maxLength={100} />
          <button className="fs-iconbtn" type="submit" aria-label="Tìm">
            <svg className="fs-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg>
          </button>
        </form>
        <select className="fs-field" aria-label="Danh mục" value={categoryId ? String(categoryId) : ''}
          onChange={(event) => update({ categoryId: event.target.value })}>
          <option value="">Tất cả danh mục</option>
          {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </select>
        <select className="fs-field" aria-label="Mức giá" value={price.value} onChange={(event) => update({ price: event.target.value })}>
          {PRICES.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <select className="fs-field" aria-label="Sắp xếp" value={sort}
          onChange={(event) => update({ sort: event.target.value === 'newest' ? '' : event.target.value })}>
          {SORTS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </div>

      {!current && <p className="fs-state">Đang tải sản phẩm…</p>}
      {current?.failed && <p className="fs-state" role="alert">
        Không thể tải sản phẩm. Hãy kiểm tra backend và PostgreSQL đang chạy.</p>}

      {result && result.items.length === 0 && <div className="fs-empty">
        <h3>{filtered ? 'Không tìm thấy sản phẩm phù hợp.' : 'Chưa có sản phẩm đang bán.'}</h3>
        {filtered && <><p>Thử bỏ bớt điều kiện lọc hoặc tìm với từ khóa khác.</p>
          <button className="fs-btn alt" type="button" onClick={() => update({ q: '', categoryId: '', price: '' })}>
            Xóa bộ lọc</button></>}
      </div>}

      {result && result.items.length > 0 && <ul className="fs-grid">
        {result.items.map((product) => <li key={product.id}>
          <Link className="fs-product" to={`/products/${product.id}`}>
            <div className="fs-product-media">
              <ProductImage src={product.imageUrl} alt={product.name} />
              {product.availableQuantity <= 0 && <span className="fs-chip red">Hết hàng</span>}
            </div>
            <div className="fs-product-meta">
              <div><span className="fs-product-title">{product.name}</span>
                <small className="fs-muted">{product.categoryName}</small></div>
              <b>{formatVnd(product.price)}</b>
            </div>
          </Link>
        </li>)}
      </ul>}

      {result && result.totalPages > 1 && <nav className="fs-pager" aria-label="Phân trang">
        <button className="fs-btn ghost" type="button" disabled={page === 0}
          onClick={() => update({ page: String(page - 1) })}>Trang trước</button>
        <span className="fs-muted">Trang {page + 1} / {result.totalPages}</span>
        <button className="fs-btn ghost" type="button" disabled={page + 1 >= result.totalPages}
          onClick={() => update({ page: String(page + 1) })}>Trang sau</button>
      </nav>}
    </main>

    <section className="fs-section fs-statement">
      <div className="fs-split">
        <div><p className="fs-eyebrow">Quan điểm của chúng tôi</p>
          <h2>Mua ít hơn.<br /><em>Mặc lâu hơn.</em></h2></div>
        <p>Chất liệu được chọn vì độ rủ, độ thoáng và độ bền. Phom dáng tôn người, màu sắc bền lâu, những món đồ bạn sẽ mặc năm này qua năm khác.</p>
      </div>
    </section>
  </StoreLayout>
}