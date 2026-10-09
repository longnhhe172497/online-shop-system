import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getProfile } from './api/profile'
import { changeRole, changeSetting, changeStatus, createUser, listAudit, listSettings, listUsers,
  updateUser, type AuditLog, type ManagedUser, type Setting } from './api/admin'
import { getUiError } from './uiError'
import InternalLayout, { type InternalNavItem } from './InternalLayout'
import { internalRoleNames, isInternalRole } from './internalAccess'

const roles: Record<string, string> = { CUSTOMER: 'Khách hàng', ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý', SUPPORT: 'Hỗ trợ', WAREHOUSE: 'Nhân viên kho', DELIVERY: 'Giao hàng' }
const statuses: Record<string, string> = { ACTIVE: 'Đang hoạt động', INACTIVE: 'Ngừng hoạt động',
  LOCKED: 'Đã khóa', PENDING_VERIFICATION: 'Chờ xác minh' }
const settingNames: Record<string, string> = { qr_expiry_minutes: 'Thời hạn mã QR (phút)',
  max_qr_attempts: 'Số lần thử QR tối đa', return_window_days: 'Thời hạn trả hàng (ngày)',
  max_saved_addresses: 'Số địa chỉ lưu tối đa', max_delivery_attempts: 'Số lần giao tối đa',
  reporting_time_zone: 'Múi giờ báo cáo' }
const actionNames: Record<string, string> = { USER_CREATED: 'Tạo tài khoản', USER_UPDATED: 'Sửa tài khoản',
  USER_ROLE_CHANGED: 'Đổi vai trò', USER_STATUS_CHANGED: 'Đổi trạng thái', SETTING_CHANGED: 'Đổi thiết lập' }
const entityNames: Record<string, string> = { USER: 'Tài khoản', SYSTEM_SETTING: 'Thiết lập' }

type Tab = 'dashboard' | 'users' | 'settings' | 'audit'
const navigation: InternalNavItem<Tab>[] = [
  { id: 'dashboard', label: 'Tổng quan', icon: 'dashboard' },
  { id: 'users', label: 'Tài khoản', icon: 'users' },
  { id: 'settings', label: 'Thiết lập', icon: 'settings' },
  { id: 'audit', label: 'Nhật ký', icon: 'audit' },
]
const pageTitles: Record<Tab, string> = { dashboard: 'Tổng quan', users: 'Quản lý tài khoản',
  settings: 'Thiết lập hệ thống', audit: 'Nhật ký thao tác' }
const pageDescriptions: Record<Tab, string> = {
  dashboard: 'Một nơi theo dõi hoạt động của hệ thống khi các tính năng được hoàn thiện.',
  users: 'Quản lý tài khoản, vai trò và trạng thái truy cập.',
  settings: 'Điều chỉnh các giới hạn vận hành của cửa hàng.',
  audit: 'Theo dõi các thao tác quản trị đã được ghi nhận.',
}
type Editor = { id?: number; email: string; fullName: string; phone: string; password: string; role: string }
const emptyEditor: Editor = { email: '', fullName: '', phone: '', password: '', role: 'SUPPORT' }

export default function AdminPage() {
  const [role, setRole] = useState<string | null>(null)
  const [accessChecked, setAccessChecked] = useState(false)
  const [tab, setTab] = useState<Tab>('dashboard')
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [settings, setSettings] = useState<Setting[]>([])
  const [audit, setAudit] = useState<AuditLog[]>([])
  const [auditPage, setAuditPage] = useState(0)
  const [auditTotal, setAuditTotal] = useState(0)
  const [editor, setEditor] = useState<Editor | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => { let active = true; getProfile().then((me) => {
    if (active) { setRole(isInternalRole(me.role) ? me.role : null); setAccessChecked(true) }
  }).catch(() => { if (active) setAccessChecked(true) }); return () => { active = false } }, [])
  useEffect(() => {
    if (role !== 'ADMIN') return
    let active = true
    if (tab === 'users') listUsers({ search, role: roleFilter, status: statusFilter, page })
      .then((data) => { if (active) { setUsers(data.items); setTotal(data.totalItems) } })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
    if (tab === 'settings') listSettings().then((data) => { if (active) setSettings(data) })
      .catch((failure) => { if (active) setError(getUiError(failure)) })
    if (tab === 'audit') listAudit(auditPage).then((data) => {
      if (active) { setAudit(data.items); setAuditTotal(data.totalItems) }
    }).catch((failure) => { if (active) setError(getUiError(failure)) })
    return () => { active = false }
  }, [role, tab, search, roleFilter, statusFilter, page, auditPage])

  async function reloadUsers() {
    const data = await listUsers({ search, role: roleFilter, status: statusFilter, page })
    setUsers(data.items); setTotal(data.totalItems)
  }
  async function perform(operation: () => Promise<unknown>, message: string) {
    setBusy(true); setError(''); setNotice('')
    try { await operation(); await reloadUsers(); setNotice(message) }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  async function saveUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editor) return
    await perform(async () => {
      if (editor.id) await updateUser(editor.id, { fullName: editor.fullName, phone: editor.phone })
      else await createUser({ email: editor.email, fullName: editor.fullName, phone: editor.phone,
        password: editor.password, role: editor.role })
      setEditor(null)
    }, editor.id ? 'Đã cập nhật tài khoản.' : 'Đã tạo tài khoản.')
  }
  async function saveSetting(setting: Setting, value: string) {
    setBusy(true); setError(''); setNotice('')
    try { const updated = await changeSetting(setting.key, value)
      setSettings((current) => current.map((item) => item.key === setting.key ? updated : item))
      setNotice('Đã cập nhật thiết lập.')
    } catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }

  return <>
      {!accessChecked && <div className="internal-access"><p>Đang kiểm tra quyền truy cập…</p></div>}
      {accessChecked && !role && <div className="internal-access"><div className="admin-panel">
        <h2>Không có quyền truy cập</h2><p>Chỉ nhân viên nội bộ được xem trang này.</p>
        <Link to="/">Về cửa hàng</Link></div></div>}
      {role && <InternalLayout items={role === 'ADMIN' ? navigation : navigation.slice(0, 1)}
        active={tab} roleName={internalRoleNames[role] ?? 'Nhân viên'} onNavigate={(next) => {
        setTab(next); setError(''); setNotice('') }} title={pageTitles[tab]} subtitle={pageDescriptions[tab]}>
      {error && <p className="auth-error" role="alert">{error}</p>}
      {notice && <p className="auth-notice" role="status">{notice}</p>}
      {tab === 'dashboard' && <Dashboard isAdmin={role === 'ADMIN'} roleName={internalRoleNames[role] ?? 'Nhân viên'} onNavigate={setTab} />}
      {role === 'ADMIN' && tab === 'users' && <section className="admin-panel"><div className="admin-section-head"><div>
        <h2>Danh sách tài khoản</h2><p>{total} tài khoản</p></div>
        <button className="account-primary" onClick={() => setEditor(emptyEditor)}>＋ Tạo tài khoản</button></div>
        <div className="admin-filters"><input aria-label="Tìm tài khoản" placeholder="Tìm tên hoặc email…"
          value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }} />
          <select aria-label="Lọc vai trò" value={roleFilter} onChange={(e) => { setRoleFilter(e.target.value); setPage(0) }}>
            <option value="">Mọi vai trò</option>{Object.entries(roles).map(([key, label]) =>
              <option key={key} value={key}>{label}</option>)}</select>
          <select aria-label="Lọc trạng thái" value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}>
            <option value="">Mọi trạng thái</option>{Object.entries(statuses).map(([key, label]) =>
              <option key={key} value={key}>{label}</option>)}</select></div>
        <div className="admin-table-wrap"><table className="address-table admin-table"><thead><tr>
          <th>TÀI KHOẢN</th><th>VAI TRÒ</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>
          {users.map((user) => <tr key={user.id}><td><strong>{user.fullName}</strong><span>{user.email}</span></td>
            <td><select aria-label={`Vai trò của ${user.fullName}`} value={user.role} disabled={busy}
              onChange={(e) => { const role = e.target.value
                void perform(() => changeRole(user.id, role), 'Đã cập nhật vai trò.') }}>
              {Object.entries(roles).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></td>
            <td><select aria-label={`Trạng thái của ${user.fullName}`} value={user.status} disabled={busy}
              onChange={(e) => { const status = e.target.value
                void perform(() => changeStatus(user.id, status), 'Đã cập nhật trạng thái.') }}>
              {Object.entries(statuses).map(([key, label]) => <option key={key} value={key} disabled={key === 'PENDING_VERIFICATION'}>{label}</option>)}</select></td>
            <td><button className="admin-link" onClick={() => setEditor({ id: user.id, email: user.email,
              fullName: user.fullName, phone: user.phone ?? '', password: '', role: user.role })}>Chỉnh sửa</button></td>
          </tr>)}</tbody></table>{users.length === 0 && <p className="admin-empty">Không tìm thấy tài khoản.</p>}</div>
        <div className="admin-pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>← Trước</button>
          <span>Trang {page + 1} / {Math.max(1, Math.ceil(total / 20))}</span>
          <button disabled={(page + 1) * 20 >= total} onClick={() => setPage(page + 1)}>Sau →</button></div></section>}
      {role === 'ADMIN' && tab === 'settings' && <section className="admin-panel"><h2>Thiết lập hệ thống</h2>
        <p>Giá trị được kiểm tra giới hạn trước khi lưu.</p><div className="settings-list">
          {settings.map((setting) => <SettingRow key={`${setting.key}:${setting.value}`} setting={setting} busy={busy}
            onSave={(value) => void saveSetting(setting, value)} />)}</div></section>}
      {role === 'ADMIN' && tab === 'audit' && <section className="admin-panel"><h2>Nhật ký thao tác</h2>
        <div className="admin-table-wrap"><table className="address-table admin-table"><thead><tr>
          <th>THỜI GIAN</th><th>NGƯỜI THỰC HIỆN</th><th>HÀNH ĐỘNG</th><th>ĐỐI TƯỢNG</th></tr></thead><tbody>
          {audit.map((item) => <tr key={item.id}><td>{new Date(item.createdAt).toLocaleString('vi-VN')}</td>
            <td>{item.actorEmail ?? 'Hệ thống'}</td><td>{actionNames[item.action] ?? 'Thao tác khác'}</td>
            <td>{entityNames[item.entityType] ?? 'Dữ liệu'} {item.entityId}</td></tr>)}</tbody></table>
          {audit.length === 0 && <p className="admin-empty">Chưa có nhật ký.</p>}</div>
        <div className="admin-pagination"><button disabled={auditPage === 0} onClick={() => setAuditPage(auditPage - 1)}>← Trước</button>
          <span>Trang {auditPage + 1} / {Math.max(1, Math.ceil(auditTotal / 20))}</span>
          <button disabled={(auditPage + 1) * 20 >= auditTotal} onClick={() => setAuditPage(auditPage + 1)}>Sau →</button></div></section>}
      </InternalLayout>}
    {editor && <div className="address-modal-backdrop"><section className="address-modal admin-editor" role="dialog"
      aria-modal="true" aria-labelledby="admin-editor-title"><div className="address-modal-head"><div>
        <p className="card-eyebrow">TÀI KHOẢN</p><h2 id="admin-editor-title">{editor.id ? 'Chỉnh sửa tài khoản' : 'Tạo tài khoản mới'}</h2></div>
        <button className="modal-close" aria-label="Đóng" onClick={() => setEditor(null)}>×</button></div>
        <form onSubmit={saveUser}><div className="address-form-grid">
          <label>HỌ VÀ TÊN<input required maxLength={150} value={editor.fullName}
            onChange={(e) => setEditor({ ...editor, fullName: e.target.value })} /></label>
          <label>SỐ ĐIỆN THOẠI<input maxLength={30} value={editor.phone}
            onChange={(e) => setEditor({ ...editor, phone: e.target.value })} /></label>
          <label>ĐỊA CHỈ EMAIL<input type="email" required disabled={Boolean(editor.id)} value={editor.email}
            onChange={(e) => setEditor({ ...editor, email: e.target.value })} /></label>
          {!editor.id && <><label>MẬT KHẨU TẠM<input type="password" required minLength={8} maxLength={72}
            value={editor.password} onChange={(e) => setEditor({ ...editor, password: e.target.value })} /></label>
            <label>VAI TRÒ<select value={editor.role} onChange={(e) => setEditor({ ...editor, role: e.target.value })}>
              {Object.entries(roles).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label></>}
        </div><div className="address-modal-actions"><button type="button" className="account-secondary"
          onClick={() => setEditor(null)}>Hủy</button><button className="account-primary" disabled={busy} type="submit">
            {editor.id ? 'Lưu thay đổi' : 'Tạo tài khoản'}</button></div></form></section></div>}
  </>
}

function Dashboard({ isAdmin, roleName, onNavigate }: { isAdmin: boolean; roleName: string;
  onNavigate: (tab: Tab) => void }) {
  return <div className="internal-dashboard">
    <div className="dashboard-intro"><div><span className="dashboard-tag">BẢN XEM TRƯỚC</span>
      <h2>Chào mừng đến với khu nội bộ</h2>
      <p>Đây là bố cục tổng quan tĩnh. Các chỉ số sẽ lấy dữ liệu thật khi những phần đơn hàng, kho và báo cáo được triển khai.</p></div>
      {isAdmin && <button type="button" className="account-primary" onClick={() => onNavigate('users')}>Quản lý tài khoản →</button>}</div>
    <div className="dashboard-metrics" aria-label="Chỉ số dự kiến">
      {[['Tài khoản', 'Sẽ cập nhật', 'Thông tin người dùng'],
        ['Đơn hàng', 'Sẽ cập nhật', 'Tình trạng xử lý'],
        ['Sản phẩm', 'Sẽ cập nhật', 'Danh mục đang bán'],
        ['Doanh thu', 'Sẽ cập nhật', 'Tổng hợp theo kỳ']].map(([name, value, description]) =>
        <div className="dashboard-metric" key={name}><span>{name}</span><strong>{value}</strong>
          <small>{description}</small></div>)}
    </div>
    <div className="dashboard-panels"><section className="dashboard-panel"><div className="dashboard-panel-head">
      <div><p className="card-eyebrow">THEO DÕI HOẠT ĐỘNG</p><h3>Hoạt động gần đây</h3></div>
      {isAdmin && <button type="button" onClick={() => onNavigate('audit')}>Xem nhật ký →</button>}</div>
      <div className="dashboard-placeholder"><span aria-hidden="true">◷</span>
        <strong>Chưa có dữ liệu tổng quan</strong><p>Nhật ký và các sự kiện mới sẽ xuất hiện tại đây sau khi kết nối dashboard.</p></div>
    </section><section className="dashboard-panel"><div className="dashboard-panel-head">
      <div><p className="card-eyebrow">KHÔNG GIAN LÀM VIỆC</p><h3>{isAdmin ? 'Công cụ quản trị' : 'Công việc của bạn'}</h3></div></div>
      {!isAdmin && <div className="dashboard-placeholder"><strong>{roleName}</strong>
        <p>Các chức năng nghiệp vụ dành cho vai trò này sẽ được bổ sung khi nhóm triển khai phần tương ứng.</p></div>}
      {isAdmin && <>
      <button type="button" className="dashboard-quick-link" onClick={() => onNavigate('users')}>
        <span>01</span><div><strong>Tài khoản</strong><small>Thêm, sửa vai trò và trạng thái</small></div><b>→</b></button>
      <button type="button" className="dashboard-quick-link" onClick={() => onNavigate('settings')}>
        <span>02</span><div><strong>Thiết lập</strong><small>Điều chỉnh giới hạn hệ thống</small></div><b>→</b></button>
      <button type="button" className="dashboard-quick-link" onClick={() => onNavigate('audit')}>
        <span>03</span><div><strong>Nhật ký</strong><small>Xem lịch sử thao tác quản trị</small></div><b>→</b></button>
      </>}
    </section></div>
  </div>
}

function SettingRow({ setting, busy, onSave }: { setting: Setting; busy: boolean; onSave: (value: string) => void }) {
  const [value, setValue] = useState(setting.value)
  return <form className="setting-row" onSubmit={(event) => { event.preventDefault(); onSave(value) }}>
    <label htmlFor={`setting-${setting.key}`}>{settingNames[setting.key] ?? setting.key}</label>
    <input id={`setting-${setting.key}`} value={value} onChange={(event) => setValue(event.target.value)}
      disabled={setting.key === 'reporting_time_zone'} required />
    <button type="submit" className="account-secondary" disabled={busy || value === setting.value}>Lưu</button>
  </form>
}
