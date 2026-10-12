import { useEffect, useState } from 'react'
import { getRolePermissions, updateRolePermissions, type RolePermissionMatrix } from './api/admin'
import { getUiError } from './uiError'

const roleNames: Record<string, string> = {
  MANAGER: 'Quản lý', SUPPORT: 'Nhân viên hỗ trợ',
  WAREHOUSE: 'Nhân viên kho', DELIVERY: 'Nhân viên giao hàng',
}

export default function RolePermissionsPage() {
  const [matrix, setMatrix] = useState<RolePermissionMatrix | null>(null)
  const [role, setRole] = useState('MANAGER')
  const [draft, setDraft] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  async function reload() {
    setLoading(true); setError('')
    try { const data = await getRolePermissions()
      setMatrix(data)
      setDraft(data.roles.find((item) => item.role === role)?.permissions ?? [])
    } catch (failure) { setError(getUiError(failure)) }
    finally { setLoading(false) }
  }
  useEffect(() => { let active = true
    getRolePermissions().then((data) => { if (active) { setMatrix(data)
      setDraft(data.roles.find((item) => item.role === 'MANAGER')?.permissions ?? []); setLoading(false) } })
      .catch((failure) => { if (active) { setError(getUiError(failure)); setLoading(false) } })
    return () => { active = false }
  }, [])

  const current = matrix?.roles.find((item) => item.role === role)
  const before = current?.permissions ?? []
  const added = draft.filter((code) => !before.includes(code))
  const removed = before.filter((code) => !draft.includes(code))
  const changed = added.length > 0 || removed.length > 0
  const groups = [...new Set(matrix?.catalog.map((item) => item.group) ?? [])]
  const labelFor = (code: string) => matrix?.catalog.find((item) => item.code === code)?.label ?? code

  function selectRole(next: string) {
    if (changed && !window.confirm('Bỏ các thay đổi chưa lưu của vai trò này?')) return
    setRole(next); setDraft(matrix?.roles.find((item) => item.role === next)?.permissions ?? [])
    setError(''); setNotice('')
  }
  function toggle(code: string) {
    setDraft((selected) => selected.includes(code) ? selected.filter((item) => item !== code) : [...selected, code])
    setNotice('')
  }
  async function save() {
    if (!current || !changed) return
    setBusy(true); setError(''); setNotice('')
    try {
      const updated = await updateRolePermissions(role, current.version, draft)
      setMatrix((existing) => existing && ({ ...existing,
        roles: existing.roles.map((item) => item.role === role ? updated : item) }))
      setDraft(updated.permissions)
      setNotice('Đã lưu quyền. Các yêu cầu tiếp theo của nhân viên sẽ áp dụng ngay.')
    } catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false); setConfirm(false) }
  }

  return <section className="admin-panel role-permissions-panel">
    <div className="admin-section-head"><div><h2>Phân quyền vai trò</h2>
      <p>Chọn quyền cho từng vai trò nội bộ. Admin và khách hàng có quyền cố định, không chỉnh tại đây.</p></div>
      <button className="account-secondary" type="button" disabled={loading || busy} onClick={() => {
        if (!changed || window.confirm('Bỏ các thay đổi chưa lưu và tải lại quyền mới nhất?')) void reload()
      }}>
        Tải lại</button></div>
    <p className="permission-note">Quyền được backend kiểm tra trên mỗi yêu cầu. Các chức năng nghiệp vụ dưới đây
      đang chờ các thành viên triển khai; cấp quyền không tự tạo ra màn hình hoặc API mới.</p>
    {loading && <p role="status">Đang tải quyền…</p>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    {notice && <p className="auth-notice" role="status">{notice}</p>}
    {matrix && <>
      <div className="permission-role-tabs" role="tablist" aria-label="Chọn vai trò">
        {matrix.roles.map((item) => <button key={item.role} type="button" role="tab"
          aria-selected={role === item.role} className={role === item.role ? 'active' : ''}
          onClick={() => selectRole(item.role)}>{roleNames[item.role] ?? item.role}
          <span>{item.permissions.length} quyền</span></button>)}
      </div>
      <div className="permission-summary"><strong>{roleNames[role]}</strong>
        <span>{draft.length} / {matrix.catalog.length} quyền được chọn</span></div>
      <div className="permission-groups">{groups.map((group) => <fieldset key={group} className="permission-group">
        <legend>{group}</legend>{matrix.catalog.filter((item) => item.group === group).map((item) =>
          <label key={item.code} className="permission-item"><input type="checkbox" checked={draft.includes(item.code)}
            disabled={busy} onChange={() => toggle(item.code)} /><span><strong>{item.label}</strong>
            <small>{item.endpoint}</small></span></label>)}</fieldset>)}</div>
      <div className="permission-actions"><span>{changed
        ? `${added.length} quyền thêm, ${removed.length} quyền bỏ` : 'Chưa có thay đổi'}</span>
        <button className="account-secondary" type="button" disabled={!changed || busy}
          onClick={() => setDraft(before)}>Hoàn tác</button>
        <button className="account-primary" type="button" disabled={!changed || busy}
          onClick={() => setConfirm(true)}>Lưu thay đổi</button></div>
    </>}
    {confirm && <div className="address-modal-backdrop"><section className="address-modal confirm-modal" role="dialog"
      aria-modal="true" aria-labelledby="permission-confirm-title"><p className="card-eyebrow">XÁC NHẬN PHÂN QUYỀN</p>
      <h2 id="permission-confirm-title">Cập nhật {roleNames[role]}?</h2>
      <p>Thay đổi sẽ áp dụng ngay cho tất cả tài khoản mang vai trò này, kể cả phiên đang đăng nhập.</p>
      {added.length > 0 && <p><strong>Thêm:</strong> {added.map(labelFor).join(', ')}</p>}
      {removed.length > 0 && <p><strong>Bỏ:</strong> {removed.map(labelFor).join(', ')}</p>}
      <div className="address-modal-actions"><button className="account-secondary" disabled={busy}
        onClick={() => setConfirm(false)}>Hủy</button><button className="account-primary" disabled={busy}
          onClick={() => void save()}>{busy ? 'ĐANG LƯU…' : 'Xác nhận lưu'}</button></div></section></div>}
  </section>
}
