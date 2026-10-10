import { api } from './client'

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }
export interface ManagedUser {
  id: number; email: string; fullName: string; phone: string | null; role: string; status: string;
  createdAt: string; verifiedAt: string | null
}
export interface Setting { key: string; value: string; description: string; updatedAt: string }
export interface AuditLog { id: number; actorId: number | null; actorEmail: string | null;
  action: string; entityType: string; entityId: string | null; createdAt: string }
export interface Invitation { id: number; email: string; fullName: string; role: string;
  expiresAt: string; acceptedAt: string | null; createdAt: string;
  status: 'PENDING' | 'STALLED' | 'SENT' | 'FAILED' | 'REVOKED' | 'EXPIRED' | 'ACCEPTED' }
export interface AdminSession { id: number; ipAddress: string | null; userAgent: string | null;
  createdAt: string; lastSeenAt: string | null; expiresAt: string }
export interface UserDetail { user: ManagedUser; sessions: AdminSession[]; history: AuditLog[] }

export async function listInvitations() {
  const { data } = await api.get<Invitation[]>('/admin/invitations')
  return data
}

export async function inviteStaff(input: { email: string; fullName: string; phone: string; role: string }) {
  const { data } = await api.post<Invitation>('/admin/invitations', input)
  return data
}
export async function resendInvitation(id: number) {
  const { data } = await api.post<Invitation>(`/admin/invitations/${id}/resend`)
  return data
}
export async function revokeInvitation(id: number) {
  const { data } = await api.post<Invitation>(`/admin/invitations/${id}/revoke`)
  return data
}

export async function acceptInvitation(input: { token: string; password: string; confirmPassword: string }) {
  await api.post('/invitations/accept', input)
}

export async function listUsers(params: { search: string; role: string; status: string; page: number }) {
  const { data } = await api.get<Page<ManagedUser>>('/admin/users', { params })
  return data
}
export async function getUserDetail(id: number) {
  const { data } = await api.get<UserDetail>(`/admin/users/${id}`)
  return data
}
export async function revokeUserSessions(id: number) {
  await api.post(`/admin/users/${id}/sessions/revoke`)
}
export async function updateUser(id: number, input: { fullName: string; phone: string }) {
  const { data } = await api.patch<ManagedUser>(`/admin/users/${id}`, input)
  return data
}
export async function changeRole(id: number, role: string) {
  const { data } = await api.patch<ManagedUser>(`/admin/users/${id}/role`, { role })
  return data
}
export async function changeStatus(id: number, status: string) {
  const { data } = await api.patch<ManagedUser>(`/admin/users/${id}/status`, { status })
  return data
}
export async function listSettings() {
  const { data } = await api.get<Setting[]>('/admin/settings')
  return data
}
export async function changeSetting(key: string, value: string) {
  const { data } = await api.patch<Setting>(`/admin/settings/${key}`, { value })
  return data
}
export interface AuditFilters { actor: string; action: string; from: string; to: string }
export async function listAudit(page: number, filters?: AuditFilters) {
  const { data } = await api.get<Page<AuditLog>>('/admin/audit-logs', { params: { page, ...filters } })
  return data
}
