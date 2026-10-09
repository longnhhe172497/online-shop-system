import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createAddress, deleteAddress, getProfile, listAddresses, updateAddress, updateProfile,
  type Address, type AddressInput, type Profile } from './api/profile'

const emptyAddress: AddressInput = {
  recipientName: '', phone: '', addressLine: '', ward: '', district: '', province: '', isDefault: false,
}

function getError(error: unknown) {
  if (isAxiosError(error)) return error.response?.data?.detail || 'The request could not be completed.'
  return 'The request could not be completed.'
}

export default function ProfilePage() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [addresses, setAddresses] = useState<Address[]>([])
  const [addressForm, setAddressForm] = useState<AddressInput>(emptyAddress)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
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

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')
    setBusy(true)
    try {
      setProfile(await updateProfile({ fullName, phone }))
      setNotice('Profile updated.')
    } catch (failure) {
      setError(getError(failure))
    } finally {
      setBusy(false)
    }
  }

  async function saveAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')
    setBusy(true)
    try {
      if (editingId === null) await createAddress(addressForm)
      else await updateAddress(editingId, addressForm)
      setAddresses(await listAddresses())
      setAddressForm(emptyAddress)
      setEditingId(null)
      setNotice('Address saved.')
    } catch (failure) {
      setError(getError(failure))
    } finally {
      setBusy(false)
    }
  }

  async function removeAddress(id: number) {
    if (!window.confirm('Delete this saved address?')) return
    setError('')
    setNotice('')
    setBusy(true)
    try {
      await deleteAddress(id)
      setAddresses(await listAddresses())
      if (editingId === id) { setEditingId(null); setAddressForm(emptyAddress) }
      setNotice('Address deleted.')
    } catch (failure) {
      setError(getError(failure))
    } finally {
      setBusy(false)
    }
  }

  function editAddress(address: Address) {
    setEditingId(address.id)
    setAddressForm({ recipientName: address.recipientName, phone: address.phone,
      addressLine: address.addressLine, ward: address.ward ?? '', district: address.district ?? '',
      province: address.province, isDefault: address.isDefault })
    setError('')
    setNotice('')
  }

  function updateAddressField<K extends keyof AddressInput>(field: K, value: AddressInput[K]) {
    setAddressForm((current) => ({ ...current, [field]: value }))
  }

  return <div className="forme-page">
    <div className="announcement">ONLINE SHOP SYSTEM · LOCAL DEVELOPMENT</div>
    <header className="forme-header"><Link to="/" className="brand">FORME</Link>
      <nav aria-label="Main navigation"><Link to="/">SHOP</Link><Link to="/auth">SIGN IN</Link></nav>
    </header>
    <main className="profile-main">
      <p className="auth-kicker">MY ACCOUNT</p>
      <h1>Profile & addresses</h1>
      {loading && <p>Loading your profile…</p>}
      {error && <p className="auth-error" role="alert">{error}</p>}
      {notice && <p className="auth-notice" role="status">{notice}</p>}
      {!loading && !profile && <p><Link to="/auth">Sign in to view your profile</Link></p>}
      {profile && <>
        <section className="profile-panel">
          <h2>Personal information</h2>
          <p className="profile-readonly">Email: {profile.email} · Role: {profile.role}</p>
          <form onSubmit={saveProfile}>
            <label>FULL NAME<input value={fullName} onChange={(event) => setFullName(event.target.value)}
              required maxLength={150} autoComplete="name" /></label>
            <label>PHONE<input value={phone} onChange={(event) => setPhone(event.target.value)}
              maxLength={30} autoComplete="tel" /></label>
            <button className="auth-submit" type="submit" disabled={busy}>SAVE PROFILE</button>
          </form>
        </section>
        {profile.role === 'CUSTOMER' && <section className="profile-panel">
          <h2>Saved addresses</h2>
          {addresses.length === 0 && <p>No saved addresses yet.</p>}
          <ul className="address-list">{addresses.map((address) => <li key={address.id}>
            <strong>{address.recipientName}{address.isDefault ? ' · Default' : ''}</strong>
            <span>{address.addressLine}, {address.ward ? `${address.ward}, ` : ''}
              {address.district ? `${address.district}, ` : ''}{address.province}</span>
            <span>{address.phone}</span>
            <div><button type="button" onClick={() => editAddress(address)}>Edit</button>
              <button type="button" onClick={() => void removeAddress(address.id)} disabled={busy}>Delete</button></div>
          </li>)}</ul>
          <h3>{editingId === null ? 'Add address' : 'Edit address'}</h3>
          <form onSubmit={saveAddress}>
            <label>RECIPIENT NAME<input value={addressForm.recipientName}
              onChange={(event) => updateAddressField('recipientName', event.target.value)} required maxLength={150} /></label>
            <label>RECIPIENT PHONE<input value={addressForm.phone}
              onChange={(event) => updateAddressField('phone', event.target.value)} required maxLength={30} /></label>
            <label>ADDRESS LINE<input value={addressForm.addressLine}
              onChange={(event) => updateAddressField('addressLine', event.target.value)} required maxLength={300} /></label>
            <label>WARD<input value={addressForm.ward}
              onChange={(event) => updateAddressField('ward', event.target.value)} maxLength={120} /></label>
            <label>DISTRICT<input value={addressForm.district}
              onChange={(event) => updateAddressField('district', event.target.value)} maxLength={120} /></label>
            <label>PROVINCE<input value={addressForm.province}
              onChange={(event) => updateAddressField('province', event.target.value)} required maxLength={120} /></label>
            <label className="default-check"><input type="checkbox" checked={addressForm.isDefault}
              onChange={(event) => updateAddressField('isDefault', event.target.checked)} /> SET AS DEFAULT</label>
            <div className="profile-actions"><button className="auth-submit" type="submit" disabled={busy}>SAVE ADDRESS</button>
              {editingId !== null && <button type="button" onClick={() => {
                setEditingId(null); setAddressForm(emptyAddress)
              }}>CANCEL EDIT</button>}</div>
          </form>
        </section>}
      </>}
    </main>
    <footer className="forme-footer"><strong>FORME</strong><span>Online Shop System · Local demo</span></footer>
  </div>
}
