import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { getProfile } from './api/profile'
import { changeRole, changeSetting, changeStatus, getUserDetail, inviteStaff, listAudit, listInvitations,
  listSettings, listUsers, resendInvitation, revokeInvitation, revokeUserSessions, updateUser,
  type AuditFilters, type AuditLog, type Invitation, type ManagedUser, type Setting, type UserDetail } from './api/admin'
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
  USER_ROLE_CHANGED: 'Đổi vai trò', USER_STATUS_CHANGED: 'Đổi trạng thái', SETTING_CHANGED: 'Đổi thiết lập',
  USER_INVITED: 'Mời nhân viên', INVITATION_ACCEPTED: 'Nhận lời mời', PASSWORD_CHANGED: 'Đổi mật khẩu',
  EMAIL_CHANGED: 'Đổi email', INVITATION_REVOKED: 'Thu hồi lời mời',
  USER_SESSIONS_REVOKED: 'Đăng xuất toàn bộ phiên', AUTH_LOGIN_SUCCESS: 'Đăng nhập thành công',
  AUTH_LOGIN_FAILED: 'Đăng nhập thất bại', MFA_EMAIL_ENABLED: 'Bật MFA qua email',
  MFA_TOTP_ENABLED: 'Bật ứng dụng xác thực', MFA_DISABLED: 'Tắt MFA',
  MFA_RECOVERY_REGENERATED: 'Tạo lại mã khôi phục' }
const entityNames: Record<string, string> = { USER: 'Tài khoản', SYSTEM_SETTING: 'Thiết lập' }
const invitationStatuses: Record<Invitation['status'], string> = {
  PENDING: 'Đang gửi', STALLED: 'Chưa rõ kết quả gửi', SENT: 'Đã gửi', FAILED: 'Gửi thất bại',
  REVOKED: 'Đã thu hồi', EXPIRED: 'Đã hết hạn', ACCEPTED: 'Đã chấp nhận',
}

type Tab = 'dashboard' | 'users' | 'invitations' | 'settings' | 'audit'
const navigation: InternalNavItem<Tab>[] = [
  { id: 'dashboard', label: 'Tổng quan', icon: 'dashboard' },
  { id: 'users', label: 'Tài khoản', icon: 'users' },
  { id: 'invitations', label: 'Lời mời', icon: 'users' },
  { id: 'settings', label: 'Thiết lập', icon: 'settings' },
  { id: 'audit', label: 'Nhật ký', icon: 'audit' },
]
const pageTitles: Record<Tab, string> = { dashboard: 'Tổng quan', users: 'Quản lý tài khoản',
  invitations: 'Lời mời nhân viên',
  settings: 'Thiết lập hệ thống', audit: 'Nhật ký thao tác' }
const pageDescriptions: Record<Tab, string> = {
  dashboard: 'Một nơi theo dõi hoạt động của hệ thống khi các tính năng được hoàn thiện.',
  users: 'Quản lý tài khoản, vai trò và trạng thái truy cập.',
  invitations: 'Mời thành viên mới và theo dõi lời mời đã gửi.',
  settings: 'Điều chỉnh các giới hạn vận hành của cửa hàng.',
  audit: 'Theo dõi các thao tác quản trị đã được ghi nhận.',
}
type Editor = { id: number; email: string; fullName: string; phone: string; role: string }
type InviteEditor = { email: string; fullName: string; phone: string; role: string }
const emptyInvite: InviteEditor = { email: '', fullName: '', phone: '', role: 'SUPPORT' }
const emptyAuditFilters: AuditFilters = { actor: '', action: '', from: '', to: '' }
type PendingChange = { user: ManagedUser; kind: 'role' | 'status'; value: string }

function tabFromPath(path: string): Tab {
  const segment = path.split('/')[2]
  return segment === 'users' || segment === 'invitations' || segment === 'settings' || segment === 'audit'
    ? segment : 'dashboard'
}

function routeFor(tab: Tab) { return tab === 'dashboard' ? '/internal' : `/internal/${tab}` }

export default function AdminPage() {
  const navigate = useNavigate()
  const pathname = useLocation().pathname
  const tab = tabFromPath(pathname)
  const detailId = tab === 'users' ? Number(pathname.split('/')[3]) || null : null
  const [role, setRole] = useState<string | null>(null)
  const [accessChecked, setAccessChecked] = useState(false)
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [settings, setSettings] = useState<Setting[]>([])
  const [audit, setAudit] = useState<AuditLog[]>([])
  const [auditPage, setAuditPage] = useState(0)
  const [auditTotal, setAuditTotal] = useState(0)
  const [auditDraft, setAuditDraft] = useState<AuditFilters>(emptyAuditFilters)
  const [auditFilters, setAuditFilters] = useState<AuditFilters>(emptyAuditFilters)
  const [detail, setDetail] = useState<UserDetail | null>(null)
  const [invitations, setInvitations] = useState<Invitation[]>([])
  const [editor, setEditor] = useState<Editor | null>(null)
  const [inviteEditor, setInviteEditor] = useState<InviteEditor | null>(null)
  const [inviteError, setInviteError] = useState('')
  const [pendingRevokeInvitation, setPendingRevokeInvitation] = useState<Invitation | null>(null)
  const [pendingChange, setPendingChange] = useState<PendingChange | null>(null)
  const [tabLoading, setTabLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => { let active = true; getProfile().then((me) => {
    if (active) { setRole(isInternalRole(me.role) ? me.role : null); setAccessChecked(true) }
  }).catch(() => { if (active) setAccessChecked(true) }); return () => { active = false } }, [])
  useEffect(() => {
    if (accessChecked && role && role !== 'ADMIN' && tab !== 'dashboard') {
      navigate('/internal', { replace: true })
    }
  }, [accessChecked, role, tab, navigate])
  useEffect(() => { const timer = window.setTimeout(() => setDebouncedSearch(search), 300)
    return () => window.clearTimeout(timer) }, [search])
  useEffect(() => {
    if (role !== 'ADMIN') return
    let active = true
    const load = async () => {
      if (!active) return
      setTabLoading(true); setError('')
      try {
        if (tab === 'users' && detailId) { const data = await getUserDetail(detailId); if (active) setDetail(data) }
        if (tab === 'users' && !detailId) { const data = await listUsers({ search: debouncedSearch, role: roleFilter,
          status: statusFilter, page }); if (active) { setUsers(data.items); setTotal(data.totalItems) } }
        if (tab === 'settings') { const data = await listSettings(); if (active) setSettings(data) }
        if (tab === 'audit') { const data = await listAudit(auditPage, auditFilters)
          if (active) { setAudit(data.items); setAuditTotal(data.totalItems) } }
        if (tab === 'invitations') { const data = await listInvitations(); if (active) setInvitations(data) }
      } catch (failure) { if (active) setError(getUiError(failure)) }
      finally { if (active) setTabLoading(false) }
    }
    void Promise.resolve().then(load)
    return () => { active = false }
  }, [role, tab, detailId, debouncedSearch, roleFilter, statusFilter, page, auditPage, auditFilters])

  async function reloadUsers() {
    const data = await listUsers({ search: debouncedSearch, role: roleFilter, status: statusFilter, page })
    setUsers(data.items); setTotal(data.totalItems)
  }
  async function perform(operation: () => Promise<unknown>, message: string) {
    setBusy(true); setError(''); setNotice('')
    try { await operation(); setNotice(message)
      try { await reloadUsers() } catch { setError('Thao tác đã lưu nhưng chưa tải lại được danh sách. Hãy tải lại trang.') } }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  async function saveUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editor) return
    await perform(async () => { await updateUser(editor.id, { fullName: editor.fullName, phone: editor.phone })
      setEditor(null) }, 'Đã cập nhật tài khoản.')
    if (detailId) { try { setDetail(await getUserDetail(detailId)) } catch { /* Page can be refreshed. */ } }
  }
  async function changeInvitation(id: number, kind: 'resend' | 'revoke') {
    setBusy(true); setError(''); setNotice('')
    try {
      if (kind === 'resend') await resendInvitation(id)
      else await revokeInvitation(id)
      setInvitations(await listInvitations())
      setNotice(kind === 'resend' ? 'Đã gửi lời mời mới.' : 'Đã thu hồi lời mời.')
    } catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false); setPendingRevokeInvitation(null) }
  }
  async function endUserSessions(id: number) {
    setBusy(true); setError(''); setNotice('')
    try { await revokeUserSessions(id); setDetail(await getUserDetail(id))
      setNotice('Đã đăng xuất tất cả phiên của tài khoản này.') }
    catch (failure) { setError(getUiError(failure)) }
    finally { setBusy(false) }
  }
  async function sendInvite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!inviteEditor) return
    setBusy(true); setError(''); setInviteError(''); setNotice('')
    try { await inviteStaff(inviteEditor)
      setInviteEditor(null); setNotice('Đã gửi lời mời qua email. Người nhận sẽ tự đặt mật khẩu.') }
    catch (failure) { setInviteError(getUiError(failure)) }
    finally {
      try { setInvitations(await listInvitations()) } catch { setError('Chưa tải lại được danh sách lời mời. Hãy tải lại trang.') }
      setBusy(false)
    }
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
        navigate(routeFor(next)); setError(''); setNotice('') }} title={pageTitles[tab]} subtitle={pageDescriptions[tab]}>
      {error && <p className="auth-error" role="alert">{error}</p>}
      {notice && <p className="auth-notice" role="status">{notice}</p>}
      {tabLoading && <p className="admin-loading" role="status">Đang tải dữ liệu…</p>}
      {tab === 'dashboard' && <Dashboard isAdmin={role === 'ADMIN'} roleName={internalRoleNames[role] ?? 'Nhân viên'}
        onNavigate={(next) => navigate(routeFor(next))} />}
      {role === 'ADMIN' && tab === 'users' && detailId && detail && <section className="admin-panel">
        <button className="admin-link" onClick={() => navigate('/internal/users')}>← Danh sách tài khoản</button>
        <div className="admin-section-head"><div><p className="card-eyebrow">HỒ SƠ TÀI KHOẢN</p>
          <h2>{detail.user.fullName}</h2><p>{detail.user.email}</p></div>
          <button className="account-secondary" onClick={() => setEditor({ id: detail.user.id,
            email: detail.user.email, fullName: detail.user.fullName, phone: detail.user.phone ?? '',
            role: detail.user.role })}>Chỉnh sửa hồ sơ</button></div>
        <div className="dashboard-metrics">
          <div className="dashboard-metric"><span>Vai trò</span><strong>{roles[detail.user.role] ?? detail.user.role}</strong></div>
          <div className="dashboard-metric"><span>Trạng thái</span><strong>{statuses[detail.user.status] ?? detail.user.status}</strong></div>
          <div className="dashboard-metric"><span>Phiên đang hoạt động</span><strong>{detail.sessions.length}</strong></div>
          <div className="dashboard-metric"><span>Ngày tạo</span>
            <strong>{new Date(detail.user.createdAt).toLocaleDateString('vi-VN')}</strong></div></div>
        <div className="admin-section-head"><div><h2>Thiết bị đăng nhập</h2>
          <p>Thời điểm hoạt động được cập nhật theo mỗi yêu cầu đã xác thực.</p></div>
          <button className="account-secondary" disabled={busy || detail.sessions.length === 0}
            onClick={() => { if (window.confirm('Đăng xuất tất cả phiên của tài khoản này?'))
              void endUserSessions(detail.user.id) }}>Đăng xuất tất cả</button></div>
        <div className="admin-table-wrap"><table className="address-table admin-table"><thead><tr>
          <th>TRÌNH DUYỆT</th><th>IP</th><th>HOẠT ĐỘNG GẦN NHẤT</th></tr></thead><tbody>
          {detail.sessions.map((session) => <tr key={session.id}>
            <td>{session.userAgent?.slice(0, 80) || 'Không rõ thiết bị'}</td>
            <td>{session.ipAddress || 'Không rõ IP'}</td>
            <td>{new Date(session.lastSeenAt ?? session.createdAt).toLocaleString('vi-VN')}</td></tr>)}
          </tbody></table>{detail.sessions.length === 0 && <p className="admin-empty">Không có phiên hoạt động.</p>}</div>
        <h2>Hoạt động gần đây</h2><div className="admin-table-wrap"><table className="address-table admin-table">
          <thead><tr><th>THỜI GIAN</th><th>HÀNH ĐỘNG</th><th>NGƯỜI THỰC HIỆN</th></tr></thead><tbody>
          {detail.history.map((item) => <tr key={item.id}>
            <td>{new Date(item.createdAt).toLocaleString('vi-VN')}</td>
            <td>{actionNames[item.action] ?? item.action}</td>
            <td>{item.actorEmail ?? 'Không xác định'}</td></tr>)}</tbody></table>
          {detail.history.length === 0 && <p className="admin-empty">Chưa có hoạt động.</p>}</div>
      </section>}
      {role === 'ADMIN' && tab === 'users' && !detailId && <section className="admin-panel"><div className="admin-section-head"><div>
        <h2>Danh sách tài khoản</h2><p>{total} tài khoản</p></div>
        <button className="account-primary" onClick={() => { setInviteError(''); setInviteEditor(emptyInvite)
          navigate('/internal/invitations') }}>＋ Mời nhân viên</button></div>
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
              onChange={(e) => setPendingChange({ user, kind: 'role', value: e.target.value })}>
              {Object.entries(roles).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></td>
            <td><select aria-label={`Trạng thái của ${user.fullName}`} value={user.status} disabled={busy}
              onChange={(e) => setPendingChange({ user, kind: 'status', value: e.target.value })}>
              {Object.entries(statuses).map(([key, label]) => <option key={key} value={key} disabled={key === 'PENDING_VERIFICATION'}>{label}</option>)}</select></td>
            <td><button className="admin-link" onClick={() => navigate(`/internal/users/${user.id}`)}>Chi tiết</button>
              <button className="admin-link" onClick={() => setEditor({ id: user.id, email: user.email,
              fullName: user.fullName, phone: user.phone ?? '', role: user.role })}>Chỉnh sửa</button></td>
          </tr>)}</tbody></table>{!tabLoading && !error && users.length === 0 &&
            <p className="admin-empty">Không tìm thấy tài khoản.</p>}</div>
        <div className="admin-pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>← Trước</button>
          <span>Trang {page + 1} / {Math.max(1, Math.ceil(total / 20))}</span>
          <button disabled={(page + 1) * 20 >= total} onClick={() => setPage(page + 1)}>Sau →</button></div></section>}
      {role === 'ADMIN' && tab === 'invitations' && <section className="admin-panel"><div className="admin-section-head">
        <div><h2>Lời mời nhân viên</h2><p>Người nhận tự đặt mật khẩu qua email. Link có hiệu lực 48 giờ.
          Nếu trạng thái gửi chưa rõ quá 5 phút, gửi lại sẽ vô hiệu hóa link cũ.</p></div>
        <button className="account-primary" onClick={() => { setInviteError(''); setInviteEditor(emptyInvite) }}>
          ＋ Gửi lời mời</button></div>
        <div className="admin-table-wrap"><table className="address-table admin-table"><thead><tr>
          <th>NGƯỜI ĐƯỢC MỜI</th><th>VAI TRÒ</th><th>GỬI LÚC</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>
          {invitations.map((item) => <tr key={item.id}><td><strong>{item.fullName}</strong><span>{item.email}</span></td>
            <td>{roles[item.role] ?? item.role}</td><td>{new Date(item.createdAt).toLocaleString('vi-VN')}</td>
            <td>{invitationStatuses[item.status] ?? item.status}
              {item.status === 'SENT' && ` · Hết hạn ${new Date(item.expiresAt).toLocaleString('vi-VN')}`}</td>
            <td>{(item.status === 'FAILED' || item.status === 'EXPIRED' || item.status === 'STALLED') &&
              <button className="admin-link" disabled={busy} onClick={() => void changeInvitation(item.id, 'resend')}>
                Gửi lại</button>}
              {(item.status === 'SENT' || item.status === 'PENDING') &&
              <button className="admin-link" disabled={busy} onClick={() => setPendingRevokeInvitation(item)}>
                Thu hồi</button>}</td></tr>)}</tbody></table>
          {!tabLoading && !error && invitations.length === 0 &&
            <p className="admin-empty">Chưa có lời mời nào.</p>}</div></section>}
      {role === 'ADMIN' && tab === 'settings' && <section className="admin-panel"><h2>Thiết lập hệ thống</h2>
        <p>Giá trị được kiểm tra giới hạn trước khi lưu.</p><div className="settings-list">
          {settings.map((setting) => <SettingRow key={`${setting.key}:${setting.value}`} setting={setting} busy={busy}
            onSave={(value) => void saveSetting(setting, value)} />)}</div></section>}
      {role === 'ADMIN' && tab === 'audit' && <section className="admin-panel"><h2>Nhật ký thao tác</h2>
        <form className="admin-filters audit-filters" onSubmit={(event) => { event.preventDefault(); setAuditPage(0)
          setAuditFilters({ ...auditDraft }) }}>
          <input aria-label="Lọc theo email người thực hiện" placeholder="Email người thực hiện…"
            value={auditDraft.actor} onChange={(event) => setAuditDraft({ ...auditDraft, actor: event.target.value })} />
          <select aria-label="Lọc hành động" value={auditDraft.action}
            onChange={(event) => setAuditDraft({ ...auditDraft, action: event.target.value })}>
            <option value="">Mọi hành động</option>{Object.entries(actionNames).map(([key, label]) =>
              <option key={key} value={key}>{label}</option>)}</select>
          <label>Từ ngày <input type="date" value={auditDraft.from}
            onChange={(event) => setAuditDraft({ ...auditDraft, from: event.target.value })} /></label>
          <label>Đến ngày <input type="date" value={auditDraft.to}
            onChange={(event) => setAuditDraft({ ...auditDraft, to: event.target.value })} /></label>
          <button className="account-secondary" type="submit">Lọc</button>
        </form>
        <div className="admin-table-wrap"><table className="address-table admin-table"><thead><tr>
          <th>THỜI GIAN</th><th>NGƯỜI THỰC HIỆN</th><th>HÀNH ĐỘNG</th><th>ĐỐI TƯỢNG</th></tr></thead><tbody>
          {audit.map((item) => <tr key={item.id}><td>{new Date(item.createdAt).toLocaleString('vi-VN')}</td>
            <td>{item.actorEmail ?? (item.action === 'AUTH_LOGIN_FAILED' ? 'Chưa xác thực' : 'Không xác định')}</td>
            <td>{actionNames[item.action] ?? item.action}</td>
            <td>{entityNames[item.entityType] ?? 'Dữ liệu'} {item.entityId}</td></tr>)}</tbody></table>
          {!tabLoading && !error && audit.length === 0 && <p className="admin-empty">Chưa có nhật ký.</p>}</div>
        <div className="admin-pagination"><button disabled={auditPage === 0} onClick={() => setAuditPage(auditPage - 1)}>← Trước</button>
          <span>Trang {auditPage + 1} / {Math.max(1, Math.ceil(auditTotal / 20))}</span>
          <button disabled={(auditPage + 1) * 20 >= auditTotal} onClick={() => setAuditPage(auditPage + 1)}>Sau →</button></div></section>}
      </InternalLayout>}
    {pendingRevokeInvitation && <div className="address-modal-backdrop"><section className="address-modal confirm-modal"
      role="dialog" aria-modal="true" aria-labelledby="revoke-invitation-title">
      <p className="card-eyebrow">XÁC NHẬN</p><h2 id="revoke-invitation-title">Thu hồi lời mời?</h2>
      <p>Liên kết đã gửi tới <strong>{pendingRevokeInvitation.email}</strong> sẽ không còn dùng được.</p>
      <div className="address-modal-actions"><button className="account-secondary" disabled={busy}
        onClick={() => setPendingRevokeInvitation(null)}>Hủy</button>
        <button className="account-primary" disabled={busy}
          onClick={() => void changeInvitation(pendingRevokeInvitation.id, 'revoke')}>Thu hồi</button></div>
    </section></div>}
    {editor && <div className="address-modal-backdrop"><section className="address-modal admin-editor" role="dialog"
      aria-modal="true" aria-labelledby="admin-editor-title"><div className="address-modal-head"><div>
        <p className="card-eyebrow">TÀI KHOẢN</p><h2 id="admin-editor-title">Chỉnh sửa tài khoản</h2></div>
        <button className="modal-close" aria-label="Đóng" onClick={() => setEditor(null)}>×</button></div>
        <form onSubmit={saveUser}><div className="address-form-grid">
          <label>HỌ VÀ TÊN<input required maxLength={150} value={editor.fullName}
            onChange={(e) => setEditor({ ...editor, fullName: e.target.value })} /></label>
          <label>SỐ ĐIỆN THOẠI<input maxLength={30} value={editor.phone}
            onChange={(e) => setEditor({ ...editor, phone: e.target.value })} /></label>
          <label>ĐỊA CHỈ EMAIL<input type="email" disabled value={editor.email} /></label>
        </div><div className="address-modal-actions"><button type="button" className="account-secondary"
          onClick={() => setEditor(null)}>Hủy</button><button className="account-primary" disabled={busy} type="submit">
            Lưu thay đổi</button></div></form></section></div>}
    {inviteEditor && <div className="address-modal-backdrop"><section className="address-modal admin-editor" role="dialog"
      aria-modal="true" aria-labelledby="invite-editor-title"><div className="address-modal-head"><div>
        <p className="card-eyebrow">THÀNH VIÊN MỚI</p><h2 id="invite-editor-title">Gửi lời mời</h2>
        <p>Người nhận sẽ tự chọn mật khẩu từ liên kết trong email.</p></div>
        <button className="modal-close" aria-label="Đóng" onClick={() => setInviteEditor(null)}>×</button></div>
        <form onSubmit={sendInvite}>{inviteError && <p className="auth-error" role="alert">{inviteError}</p>}
          <div className="address-form-grid">
          <label>HỌ VÀ TÊN<input required maxLength={150} value={inviteEditor.fullName}
            onChange={(e) => setInviteEditor({ ...inviteEditor, fullName: e.target.value })} /></label>
          <label>ĐỊA CHỈ EMAIL<input type="email" required value={inviteEditor.email}
            onChange={(e) => setInviteEditor({ ...inviteEditor, email: e.target.value })} /></label>
          <label>SỐ ĐIỆN THOẠI<input maxLength={30} value={inviteEditor.phone}
            onChange={(e) => setInviteEditor({ ...inviteEditor, phone: e.target.value })} /></label>
          <label>VAI TRÒ<select value={inviteEditor.role} onChange={(e) => setInviteEditor({ ...inviteEditor,
            role: e.target.value })}>{Object.entries(roles).filter(([key]) => key !== 'CUSTOMER')
              .map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        </div><div className="address-modal-actions"><button type="button" className="account-secondary"
          onClick={() => setInviteEditor(null)}>Hủy</button><button className="account-primary" disabled={busy}>
            {busy ? 'ĐANG GỬI…' : 'Gửi lời mời'}</button></div></form></section></div>}
    {pendingChange && <div className="address-modal-backdrop"><section className="address-modal confirm-modal"
      role="dialog" aria-modal="true" aria-labelledby="confirm-change-title">
      <p className="card-eyebrow">XÁC NHẬN THAY ĐỔI</p><h2 id="confirm-change-title">Cập nhật quyền truy cập?</h2>
      <p>Bạn sắp đổi {pendingChange.kind === 'role' ? 'vai trò' : 'trạng thái'} của
        <strong> {pendingChange.user.fullName}</strong> thành <strong>{pendingChange.kind === 'role'
          ? roles[pendingChange.value] : statuses[pendingChange.value]}</strong>.
        Các phiên hiện tại của tài khoản này sẽ bị đăng xuất.</p>
      <div className="address-modal-actions"><button className="account-secondary" onClick={() => setPendingChange(null)}>
        Hủy</button><button className="account-primary" disabled={busy} onClick={() => {
          const change = pendingChange
          setPendingChange(null)
          void perform(() => change.kind === 'role' ? changeRole(change.user.id, change.value) :
            changeStatus(change.user.id, change.value), 'Đã cập nhật quyền truy cập.')
        }}>Xác nhận</button></div></section></div>}
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
