import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createAddress, deleteAddress, getProfile, listAddresses, updateAddress, updateProfile,
  type Address, type AddressInput, type Profile } from './api/profile'

const emptyAddress: AddressInput = {
  recipientName: '', phone: '', addressLine: '', ward: '', district: '', province: '', isDefault: false,
}

function getError(error: unknown) {
  if (isAxiosError(error)) return error.response?.data?.detail || 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.'
  return 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.'
}

function formatAddress(address: Address) {
  return [address.addressLine, address.ward, address.district, address.province]
    .filter(Boolean).join(', ')
}

export default function ProfilePage() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [addresses, setAddresses] = useState<Address[]>([])
  const [addressForm, setAddressForm] = useState<AddressInput>(emptyAddress)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [addressError, setAddressError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let active = true
    async function load() {
      try {
        const me = await getProfile()
        if (!active) return
        setProfile(me)
        setFullName(me.fullName)
        setPhone(me.phone ?? '')
        if (me.role === 'CUSTOMER') {
          const saved = await listAddresses()
          if (active) setAddresses(saved)
        }
      } catch (failure) {
        if (active) setError(getError(failure))
      } finally {
        if (active) setLoading(false)
      }
    }
    void load()
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!editorOpen) return
    function onEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) setEditorOpen(false)
    }
    window.addEventListener('keydown', onEscape)
    return () => window.removeEventListener('keydown', onEscape)
  }, [editorOpen, busy])

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')
    setBusy(true)
    try {
      setProfile(await updateProfile({ fullName, phone }))
      setNotice('Đã cập nhật hồ sơ cá nhân.')
    } catch (failure) {
      setError(getError(failure))
    } finally {
      setBusy(false)
    }
  }

  async function saveAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAddressError('')
    setNotice('')
    setBusy(true)
    try {
      if (editingId === null) await createAddress(addressForm)
      else await updateAddress(editingId, addressForm)
      setAddresses(await listAddresses())
      setAddressForm(emptyAddress)
      setEditingId(null)
      setEditorOpen(false)
      setNotice('Đã lưu địa chỉ giao hàng.')
    } catch (failure) {
      setAddressError(getError(failure))
    } finally {
      setBusy(false)
    }
  }

  async function removeAddress(id: number) {
    if (!window.confirm('Bạn có chắc muốn xóa địa chỉ này?')) return
    setError('')
    setNotice('')
    setBusy(true)
    try {
      await deleteAddress(id)
      setAddresses(await listAddresses())
      setNotice('Đã xóa địa chỉ giao hàng.')
    } catch (failure) {
      setError(getError(failure))
    } finally {
      setBusy(false)
    }
  }

  function addAddress() {
    setEditingId(null)
    setAddressForm(emptyAddress)
    setAddressError('')
    setEditorOpen(true)
  }

  function editAddress(address: Address) {
    setEditingId(address.id)
    setAddressForm({ recipientName: address.recipientName, phone: address.phone,
      addressLine: address.addressLine, ward: address.ward ?? '', district: address.district ?? '',
      province: address.province, isDefault: address.isDefault })
    setAddressError('')
    setEditorOpen(true)
  }

  function updateAddressField<K extends keyof AddressInput>(field: K, value: AddressInput[K]) {
    setAddressForm((current) => ({ ...current, [field]: value }))
  }

  return <div className="forme-page">
    <div className="announcement">ONLINE SHOP SYSTEM · LOCAL DEVELOPMENT</div>
    <header className="forme-header"><Link to="/" className="brand">FORME</Link>
      <nav aria-label="Main navigation"><Link to="/">SHOP</Link><Link to="/me">TÀI KHOẢN</Link></nav>
    </header>
    <main className="account-main">
      <div className="account-heading">
        <div><p className="auth-kicker">TÀI KHOẢN CỦA TÔI</p><h1>Hồ sơ & địa chỉ giao hàng</h1>
          <p>Quản lý thông tin cá nhân và địa chỉ nhận hàng của bạn tại một nơi.</p></div>
        <Link to="/" className="account-back">← Tiếp tục mua sắm</Link>
      </div>
      {loading && <p className="account-message">Đang tải thông tin tài khoản…</p>}
      {error && <p className="auth-error account-message" role="alert">{error}</p>}
      {notice && <p className="auth-notice account-message" role="status">{notice}</p>}
      {!loading && !profile && <p className="account-message"><Link to="/auth">Đăng nhập để xem tài khoản</Link></p>}
      {profile && <div className="account-grid">
        <section className="account-card personal-card" aria-labelledby="personal-title">
          <div className="personal-intro"><div className="account-avatar" aria-hidden="true">
            {profile.fullName.trim().charAt(0).toLocaleUpperCase('vi')}</div>
            <div><p className="card-eyebrow">THÔNG TIN CÁ NHÂN</p><h2 id="personal-title">{profile.fullName}</h2>
              <span className="role-pill">{profile.role === 'CUSTOMER' ? 'Khách hàng' : profile.role}</span></div></div>
          <div className="account-readonly"><span>Email đăng nhập</span><strong>{profile.email}</strong>
            <small>Email và vai trò không thể chỉnh sửa tại đây.</small></div>
          <form className="account-form" onSubmit={saveProfile}>
            <label>HỌ VÀ TÊN<input value={fullName} onChange={(event) => setFullName(event.target.value)}
              required maxLength={150} autoComplete="name" /></label>
            <label>SỐ ĐIỆN THOẠI<input value={phone} onChange={(event) => setPhone(event.target.value)}
              maxLength={30} autoComplete="tel" placeholder="Thêm số điện thoại" /></label>
            <button className="account-primary" type="submit" disabled={busy}>Lưu thay đổi</button>
          </form>
        </section>

        {profile.role === 'CUSTOMER' && <section className="account-card addresses-card" aria-labelledby="address-title">
          <div className="address-section-head"><div><p className="card-eyebrow">SỔ ĐỊA CHỈ</p>
            <h2 id="address-title">Địa chỉ của bạn</h2>
            <p>{addresses.length} địa chỉ đã lưu · Tối đa 10 địa chỉ</p></div>
            <button className="account-primary add-address" type="button" onClick={addAddress}>
              <span aria-hidden="true">＋</span> Thêm địa chỉ</button></div>
          {addresses.length === 0 ? <div className="address-empty"><div aria-hidden="true">⌂</div>
            <strong>Chưa có địa chỉ giao hàng</strong><p>Thêm địa chỉ đầu tiên để đặt hàng nhanh hơn.</p>
            <button type="button" onClick={addAddress}>Thêm địa chỉ ngay →</button></div>
            : <div className="address-table-wrap"><table className="address-table">
              <thead><tr><th scope="col">NGƯỜI NHẬN</th><th scope="col">ĐỊA CHỈ GIAO HÀNG</th>
                <th scope="col">TRẠNG THÁI</th><th scope="col">THAO TÁC</th></tr></thead>
              <tbody>{addresses.map((address) => <tr key={address.id}>
                <td><strong>{address.recipientName}</strong><span>{address.phone}</span></td>
                <td className="address-location">{formatAddress(address)}</td>
                <td>{address.isDefault ? <span className="default-badge">Mặc định</span>
                  : <span className="secondary-badge">Địa chỉ phụ</span>}</td>
                <td><div className="address-row-actions"><button type="button"
                  onClick={() => editAddress(address)}>Chỉnh sửa</button>
                  <button type="button" onClick={() => void removeAddress(address.id)}
                    disabled={busy}>Xóa</button></div></td>
              </tr>)}</tbody>
            </table></div>}
        </section>}
      </div>}
    </main>

    {editorOpen && <div className="address-modal-backdrop"><section className="address-modal"
      role="dialog" aria-modal="true" aria-labelledby="address-modal-title">
      <div className="address-modal-head"><div><p className="card-eyebrow">ĐỊA CHỈ GIAO HÀNG</p>
        <h2 id="address-modal-title">{editingId === null ? 'Thêm địa chỉ mới' : 'Chỉnh sửa địa chỉ'}</h2>
        <p>Điền đầy đủ thông tin để giao hàng chính xác.</p></div>
        <button className="modal-close" type="button" aria-label="Đóng" disabled={busy}
          onClick={() => setEditorOpen(false)}>×</button></div>
      <form onSubmit={saveAddress}>
        {addressError && <p className="auth-error" role="alert">{addressError}</p>}
        <div className="address-form-grid">
          <label>HỌ TÊN NGƯỜI NHẬN<input value={addressForm.recipientName} autoFocus
            onChange={(event) => updateAddressField('recipientName', event.target.value)}
            required maxLength={150} placeholder="Ví dụ: Nguyễn Văn A" /></label>
          <label>SỐ ĐIỆN THOẠI<input value={addressForm.phone}
            onChange={(event) => updateAddressField('phone', event.target.value)}
            required maxLength={30} autoComplete="tel" placeholder="Số điện thoại nhận hàng" /></label>
          <label className="form-span-2">ĐỊA CHỈ CỤ THỂ<input value={addressForm.addressLine}
            onChange={(event) => updateAddressField('addressLine', event.target.value)}
            required maxLength={300} placeholder="Số nhà, tên đường, tòa nhà…" /></label>
          <label>PHƯỜNG / XÃ<input value={addressForm.ward}
            onChange={(event) => updateAddressField('ward', event.target.value)} maxLength={120} /></label>
          <label>QUẬN / HUYỆN<input value={addressForm.district}
            onChange={(event) => updateAddressField('district', event.target.value)} maxLength={120} /></label>
          <label className="form-span-2">TỈNH / THÀNH PHỐ<input value={addressForm.province}
            onChange={(event) => updateAddressField('province', event.target.value)}
            required maxLength={120} placeholder="Ví dụ: Hà Nội" /></label>
        </div>
        <label className="default-option"><input type="checkbox" checked={addressForm.isDefault}
          onChange={(event) => updateAddressField('isDefault', event.target.checked)} />
          <span><strong>Đặt làm địa chỉ mặc định</strong><small>Địa chỉ này sẽ được ưu tiên khi đặt hàng.</small></span></label>
        <div className="address-modal-actions"><button type="button" className="account-secondary"
          disabled={busy} onClick={() => setEditorOpen(false)}>Hủy</button>
          <button type="submit" className="account-primary" disabled={busy}>
            {busy ? 'Đang lưu…' : editingId === null ? 'Thêm địa chỉ' : 'Lưu thay đổi'}</button></div>
      </form>
    </section></div>}
    <footer className="forme-footer"><strong>FORME</strong><span>Online Shop System · Local demo</span></footer>
  </div>
}
