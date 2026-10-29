import { api } from './client'
import type { Page } from './products'

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
export type CategoryStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'

export interface ManagerProduct {
  id: number
  sku: string
  name: string
  description: string | null
  imageUrl: string | null
  price: number
  categoryId: number
  categoryName: string
  categoryStatus: CategoryStatus
  status: ProductStatus
  stockOnHand: number
  stockReserved: number
  availableQuantity: number
  createdAt: string
  updatedAt: string
}

/** Stock is not part of the input: Warehouse owns it. */
export interface ProductInput {
  sku: string
  name: string
  description: string | null
  imageUrl: string | null
  price: number
  categoryId: number
}

export interface ProductFilters {
  q?: string
  categoryId?: number
  status?: ProductStatus
}

export interface CategoryOption {
  id: number
  name: string
  status: CategoryStatus
}

export interface ManagerCategory {
  id: number
  name: string
  description: string | null
  status: CategoryStatus
  productCount: number
  activeProductCount: number
  createdAt: string
  updatedAt: string
}

export interface CategoryInput {
  name: string
  description: string | null
}

export async function listManagerProducts(page: number, filters: ProductFilters = {}) {
  const { data } = await api.get<Page<ManagerProduct>>('/manager/products', { params: { page, size: 20, ...filters } })
  return data
}

export async function listCategoryOptions() {
  const { data } = await api.get<CategoryOption[]>('/manager/products/category-options')
  return data
}

export async function createProduct(input: ProductInput) {
  const { data } = await api.post<ManagerProduct>('/manager/products', input)
  return data
}

export async function updateProduct(id: number, input: ProductInput) {
  const { data } = await api.patch<ManagerProduct>(`/manager/products/${id}`, input)
  return data
}

export async function changeProductStatus(id: number, status: ProductStatus) {
  const { data } = await api.patch<ManagerProduct>(`/manager/products/${id}/status`, { status })
  return data
}

export async function listManagerCategories(status?: CategoryStatus) {
  const { data } = await api.get<ManagerCategory[]>('/manager/categories', { params: status ? { status } : {} })
  return data
}

export async function createCategory(input: CategoryInput) {
  const { data } = await api.post<ManagerCategory>('/manager/categories', input)
  return data
}

export async function updateCategory(id: number, input: CategoryInput) {
  const { data } = await api.patch<ManagerCategory>(`/manager/categories/${id}`, input)
  return data
}

export async function changeCategoryStatus(id: number, status: CategoryStatus) {
  const { data } = await api.patch<ManagerCategory>(`/manager/categories/${id}/status`, { status })
  return data
}

export async function deleteCategory(id: number) {
  await api.delete(`/manager/categories/${id}`)
}
