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
