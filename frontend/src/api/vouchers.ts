import { api } from './client'
import type { Page } from './products'

export type VoucherStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'EXPIRED'
export interface Voucher {
  id: number; code: string; discountType: 'FIXED' | 'PERCENT'; discountValue: number
  maxDiscount: number | null; minOrderAmount: number; usageLimit: number; usedCount: number
  validFrom: string; validUntil: string; status: VoucherStatus; createdAt: string
}
export interface VoucherInput {
  code: string; discountType: 'FIXED' | 'PERCENT'; discountValue: number; maxDiscount: number | null
  minOrderAmount: number; usageLimit: number; validFrom: string; validUntil: string
  status: 'DRAFT' | 'ACTIVE' | 'INACTIVE'
}

export async function listVouchers(search = '', page = 0) {
  const { data } = await api.get<Page<Voucher>>('/manager/vouchers', { params: { search: search || undefined, page, size: 20 } })
  return data
}

export async function createVoucher(input: VoucherInput) {
  const { data } = await api.post<Voucher>('/manager/vouchers', input)
  return data
}

export async function updateVoucher(id: number, input: VoucherInput) {
  const { data } = await api.patch<Voucher>(`/manager/vouchers/${id}`, input)
  return data
}

export async function deleteVoucher(id: number) {
  await api.delete(`/manager/vouchers/${id}`)
}
