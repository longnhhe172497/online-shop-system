import { api } from './client'

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }
export interface ManagedUser {
  id: number; email: string; fullName: string; phone: string | null; role: string; status: string;
  createdAt: string; verifiedAt: string | null
}
export interface Setting { key: string; value: string; description: string; updatedAt: string }
export interface AuditLog { id: number; actorId: number | null; actorEmail: string | null;
  action: string; entityType: string; entityId: string | null; createdAt: string }

export async function listUsers(params: { search: string; role: string; status: string; page: number }) {
  const { data } = await api.get<Page<ManagedUser>>('/admin/users', { params })
  return data
}
export async function createUser(input: { email: string; fullName: string; phone: string;
  password: string; role: string }) {
  const { data } = await api.post<ManagedUser>('/admin/users', input)
  return data
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
export async function listAudit(page: number) {
  const { data } = await api.get<Page<AuditLog>>('/admin/audit-logs', { params: { page } })
  return data
}
