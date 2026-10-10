import { api } from './client'

export interface Profile {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: string
}

export interface AddressInput {
  recipientName: string
  phone: string
  addressLine: string
  ward: string
  district: string
  province: string
  isDefault: boolean
}

export interface Address extends Omit<AddressInput, 'ward' | 'district'> {
  id: number
  ward: string | null
  district: string | null
}

export async function getProfile() {
  const { data } = await api.get<Profile>('/me')
  return data
}

export async function updateProfile(input: { fullName: string; phone: string }) {
  const { data } = await api.patch<Profile>('/me', input)
  return data
}

export async function listAddresses() {
  const { data } = await api.get<Address[]>('/me/addresses')
  return data
}

export async function getAccountLimits() {
  const { data } = await api.get<{ maxSavedAddresses: number }>('/me/limits')
  return data
}

export async function getSecurity() {
  const { data } = await api.get<{ mfaEnabled: boolean }>('/me/security')
  return data
}

export async function changePassword(input: { currentPassword: string; newPassword: string;
  confirmPassword: string }) {
  await api.post('/me/password', input)
}

export interface Session { id: number; ipAddress: string | null; userAgent: string | null;
  createdAt: string; expiresAt: string; current: boolean }

export async function listSessions() {
  const { data } = await api.get<Session[]>('/me/sessions')
  return data
}

export async function revokeSession(id: number) {
  await api.delete(`/me/sessions/${id}`)
}

export async function revokeOtherSessions() {
  await api.post('/me/sessions/revoke-other')
}

export async function startMfa() {
  const { data } = await api.post<{ challengeToken: string }>('/me/mfa/start')
  return data
}

export async function confirmMfa(challengeToken: string, code: string) {
  await api.post('/me/mfa/confirm', { challengeToken, code })
}

export async function disableMfa(password: string) {
  await api.post('/me/mfa/disable', { password })
}

export async function requestEmailChange(newEmail: string, currentPassword: string) {
  await api.post('/me/email-change', { newEmail, currentPassword })
}

export async function confirmEmailChange(token: string) {
  await api.post('/auth/email-change/confirm', { token })
}

export async function createAddress(input: AddressInput) {
  const { data } = await api.post<Address>('/me/addresses', input)
  return data
}

export async function updateAddress(id: number, input: AddressInput) {
  const { data } = await api.patch<Address>(`/me/addresses/${id}`, input)
  return data
}

export async function deleteAddress(id: number) {
  await api.delete(`/me/addresses/${id}`)
}
