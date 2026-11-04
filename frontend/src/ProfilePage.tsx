import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { createAddress, deleteAddress, getAccountLimits, getProfile, listAddresses, updateAddress, updateProfile,
  type Address, type AddressInput, type Profile } from './api/profile'
import { getUiError } from './uiError'
import AccountLayout from './AccountLayout'
import { personNameError, phoneDigits, phoneError, requiredTextError } from './inputValidation'

const emptyAddress: AddressInput = {
  recipientName: '', phone: '', addressLine: '', ward: '', district: '', province: '', isDefault: false,
}

function validateProfile(fullName: string, phone: string) {
  return {
    name: personNameError(fullName),
    phone: phoneError(phone, false),
  }
}

function formatAddress(address: Address) {
  return [address.addressLine, address.ward, address.district, address.province]
    .filter(Boolean).join(', ')
}

export default function ProfilePage() {
  const showAddresses = useLocation().pathname.endsWith('/addresses')
  const [profile, setProfile] = useState<Profile | null>(null)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [profileErrors, setProfileErrors] = useState({ name: '', phone: '' })
  const [addresses, setAddresses] = useState<Address[]>([])
  const [maxSavedAddresses, setMaxSavedAddresses] = useState<number | null>(null)
  const [addressForm, setAddressForm] = useState<AddressInput>(emptyAddress)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [addressError, setAddressError] = useState('')
  const [addressFieldErrors, setAddressFieldErrors] = useState({
    name: '', phone: '', addressLine: '', province: '',
  })
  const [notice, setNotice] = useState('')
  const editingDefaultAddress = editingId !== null &&
    addresses.some((address) => address.id === editingId && address.isDefault)

  useEffect(() => {
    let active = true
    async function load() {
      try {
        const me = await getProfile()
        if (!active) return
        setProfile(me)
        setFullName(me.fullName)
        setPhone(me.phone ?? '')
        if (me.role === 'CUSTOMER' && showAddresses) {
          const [saved, limits] = await Promise.all([listAddresses(), getAccountLimits()])
          if (active) { setAddresses(saved); setMaxSavedAddresses(limits.maxSavedAddresses) }
        }
      } catch (failure) {
        if (active) setError(getUiError(failure))
      } finally {
        if (active) setLoading(false)
      }
    }
    void load()
    return () => { active = false }
  }, [showAddresses])

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
    const normalizedName = fullName.trim()
    const validation = validateProfile(normalizedName, phone)
    setProfileErrors(validation)
    if (validation.name || validation.phone) return
    setBusy(true)
    try {
      const updated = await updateProfile({ fullName: normalizedName, phone })
      setProfile(updated)
      setFullName(updated.fullName)
      setPhone(updated.phone ?? '')
      setNotice('Đã cập nhật hồ sơ cá nhân.')
    } catch (failure) {
      setError(getUiError(failure))
    } finally {
      setBusy(false)
    }
  }

  async function saveAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAddressError('')
    setNotice('')
    const validation = {
      name: personNameError(addressForm.recipientName, 'Họ tên người nhận'),
      phone: phoneError(addressForm.phone, true),
      addressLine: requiredTextError(addressForm.addressLine, 'Địa chỉ cụ thể'),
      province: requiredTextError(addressForm.province, 'Tỉnh / Thành phố'),
    }
    setAddressFieldErrors(validation)
    if (Object.values(validation).some(Boolean)) return
    const normalizedAddress = {
      ...addressForm,
      recipientName: addressForm.recipientName.trim(),
      addressLine: addressForm.addressLine.trim(),
      ward: addressForm.ward.trim(),
      district: addressForm.district.trim(),
      province: addressForm.province.trim(),
    }
    setBusy(true)
    try {
      if (editingId === null) await createAddress(normalizedAddress)
      else await updateAddress(editingId, normalizedAddress)
      setAddresses(await listAddresses())
      setAddressForm(emptyAddress)
      setEditingId(null)
      setEditorOpen(false)
      setNotice('Đã lưu địa chỉ giao hàng.')
    } catch (failure) {
      setAddressError(getUiError(failure))
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
      setError(getUiError(failure))
    } finally {
      setBusy(false)
    }
  }

  function addAddress() {
    setEditingId(null)
    setAddressForm(emptyAddress)
    setAddressError('')
    setAddressFieldErrors({ name: '', phone: '', addressLine: '', province: '' })
    setEditorOpen(true)
  }

  function editAddress(address: Address) {
    setEditingId(address.id)
    setAddressForm({ recipientName: address.recipientName, phone: address.phone,
      addressLine: address.addressLine, ward: address.ward ?? '', district: address.district ?? '',
      province: address.province, isDefault: address.isDefault })
    setAddressError('')
    setAddressFieldErrors({ name: '', phone: '', addressLine: '', province: '' })
    setEditorOpen(true)
  }

  function updateAddressField<K extends keyof AddressInput>(field: K, value: AddressInput[K]) {
    setAddressForm((current) => ({ ...current, [field]: value }))
  }

  return <AccountLayout title={showAddresses ? 'Địa chỉ giao hàng' : 'Hồ sơ cá nhân'}
    intro={showAddresses ? 'Lưu và chỉnh sửa địa chỉ nhận hàng của bạn.' : 'Quản lý thông tin cá nhân tại FORME.'}>
      {loading && <p className="account-message">Đang tải thông tin tài khoản…</p>}
      {error && <p className="auth-error account-message" role="alert">{error}</p>}
      {notice && <p className="auth-notice account-message" role="status">{notice}</p>}
      {!loading && !profile && <p className="account-message"><Link to="/auth">Đăng nhập để xem tài khoản</Link></p>}
      {profile && <div className="account-grid">
        {!showAddresses && <div className="profile-layout">
          <section className="account-card personal-card" aria-labelledby="personal-title">
            <p className="card-eyebrow">THÔNG TIN CÁ NHÂN</p><h2 id="personal-title">Chỉnh sửa hồ sơ</h2>
            <form className="account-form" onSubmit={saveProfile}>
              <label>HỌ VÀ TÊN<input value={fullName} onChange={(event) => {
                const value = event.target.value
                setFullName(value)
                setProfileErrors((current) => ({ ...current,
                  name: personNameError(value) }))
              }} required maxLength={150} autoComplete="name" aria-invalid={Boolean(profileErrors.name)}
                aria-describedby={profileErrors.name ? 'profile-name-error' : undefined} />
                {profileErrors.name && <span className="field-error" id="profile-name-error"
                  role="alert">{profileErrors.name}</span>}</label>
              <label>EMAIL ĐĂNG NHẬP<input value={profile.email} disabled aria-describedby="profile-email-hint" /></label>
              <label>SỐ ĐIỆN THOẠI<input value={phone} onChange={(event) => {
                const digits = phoneDigits(event.target.value)
                setPhone(digits)
                setProfileErrors((current) => ({ ...current,
                  phone: digits && digits.length !== 10
                    ? 'Số điện thoại phải gồm đúng 10 chữ số.' : '' }))
              }} maxLength={10} inputMode="numeric" autoComplete="tel" placeholder="Ví dụ: 0901234567"
                pattern="[0-9]{10}" aria-invalid={Boolean(profileErrors.phone)}
                aria-describedby={profileErrors.phone ? 'profile-phone-error' : undefined} />
                {profileErrors.phone && <span className="field-error" id="profile-phone-error"
                  role="alert">{profileErrors.phone}</span>}</label>
              <p className="profile-field-note" id="profile-email-hint">Đổi email tại mục “Đổi email đăng nhập”.</p>
              <button className="account-primary" type="submit" disabled={busy}>Lưu thay đổi</button>
            </form>
          </section>
          <aside className="account-card profile-summary" aria-label="Tóm tắt tài khoản">
            <div className="account-avatar" aria-hidden="true">{profile.fullName.trim().charAt(0).toLocaleUpperCase('vi')}</div>
            <h2>{profile.fullName}</h2><p>{profile.email}</p>
            <span className="role-pill">{({ CUSTOMER: 'Khách hàng', ADMIN: 'Quản trị viên', MANAGER: 'Quản lý', SUPPORT: 'Hỗ trợ', WAREHOUSE: 'Nhân viên kho', DELIVERY: 'Nhân viên giao hàng' } as Record<string, string>)[profile.role] ?? 'Vai trò khác'}</span>
          </aside>
        </div>}

        {showAddresses && profile.role !== 'CUSTOMER' && <section className="account-card addresses-card">
          <h2>Địa chỉ giao hàng</h2><p>Chỉ tài khoản khách hàng sử dụng sổ địa chỉ giao hàng.</p></section>}
        {showAddresses && profile.role === 'CUSTOMER' && <section className="account-card addresses-card" aria-labelledby="address-title">
          <div className="address-section-head"><div><p className="card-eyebrow">SỔ ĐỊA CHỈ</p>
            <h2 id="address-title">Địa chỉ của bạn</h2>
            <p>{addresses.length} địa chỉ đã lưu{maxSavedAddresses === null ? '' :
              ` · Tối đa ${maxSavedAddresses} địa chỉ`}</p></div>
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
            onChange={(event) => { updateAddressField('recipientName', event.target.value)
              setAddressFieldErrors((current) => ({ ...current,
                name: personNameError(event.target.value, 'Họ tên người nhận') })) }}
            required maxLength={150} placeholder="Ví dụ: Nguyễn Văn A"
            aria-invalid={Boolean(addressFieldErrors.name)} />
            {addressFieldErrors.name && <span className="field-error" role="alert">
              {addressFieldErrors.name}</span>}</label>
          <label>SỐ ĐIỆN THOẠI<input value={addressForm.phone}
            onChange={(event) => { const digits = phoneDigits(event.target.value)
              updateAddressField('phone', digits)
              setAddressFieldErrors((current) => ({ ...current, phone: phoneError(digits, true) })) }}
            required maxLength={10} inputMode="numeric" pattern="[0-9]{10}" autoComplete="tel"
            placeholder="Số điện thoại nhận hàng" aria-invalid={Boolean(addressFieldErrors.phone)} />
            {addressFieldErrors.phone && <span className="field-error" role="alert">
              {addressFieldErrors.phone}</span>}</label>
          <label className="form-span-2">ĐỊA CHỈ CỤ THỂ<input value={addressForm.addressLine}
            onChange={(event) => { updateAddressField('addressLine', event.target.value)
              setAddressFieldErrors((current) => ({ ...current,
                addressLine: requiredTextError(event.target.value, 'Địa chỉ cụ thể') })) }}
            required maxLength={300} placeholder="Số nhà, tên đường, tòa nhà…"
            aria-invalid={Boolean(addressFieldErrors.addressLine)} />
            {addressFieldErrors.addressLine && <span className="field-error" role="alert">
              {addressFieldErrors.addressLine}</span>}</label>
          <label>PHƯỜNG / XÃ<input value={addressForm.ward}
            onChange={(event) => updateAddressField('ward', event.target.value)} maxLength={120} /></label>
          <label>QUẬN / HUYỆN<input value={addressForm.district}
            onChange={(event) => updateAddressField('district', event.target.value)} maxLength={120} /></label>
          <label className="form-span-2">TỈNH / THÀNH PHỐ<input value={addressForm.province}
            onChange={(event) => { updateAddressField('province', event.target.value)
              setAddressFieldErrors((current) => ({ ...current,
                province: requiredTextError(event.target.value, 'Tỉnh / Thành phố') })) }}
            required maxLength={120} placeholder="Ví dụ: Hà Nội"
            aria-invalid={Boolean(addressFieldErrors.province)} />
            {addressFieldErrors.province && <span className="field-error" role="alert">
              {addressFieldErrors.province}</span>}</label>
        </div>
        <label className="default-option"><input type="checkbox" checked={addressForm.isDefault}
          disabled={editingDefaultAddress}
          onChange={(event) => updateAddressField('isDefault', event.target.checked)} />
          <span><strong>Đặt làm địa chỉ mặc định</strong><small>{editingDefaultAddress
            ? 'Muốn đổi mặc định, hãy chỉnh một địa chỉ khác và chọn làm mặc định.'
            : 'Địa chỉ này sẽ được ưu tiên khi đặt hàng.'}</small></span></label>
        <div className="address-modal-actions"><button type="button" className="account-secondary"
          disabled={busy} onClick={() => setEditorOpen(false)}>Hủy</button>
          <button type="submit" className="account-primary" disabled={busy}>
            {busy ? 'Đang lưu…' : editingId === null ? 'Thêm địa chỉ' : 'Lưu thay đổi'}</button></div>
      </form>
    </section></div>}
  </AccountLayout>
}
