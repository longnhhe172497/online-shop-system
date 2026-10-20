import { api } from './client'
import type { Product } from './products'

export interface ProductDetail extends Product {
  categoryId: number
  avgRating: number | null
  reviewCount: number
}

export interface Category {
  id: number
  name: string
  description: string | null
}

export async function getProduct(id: number): Promise<ProductDetail> {
  const response = await api.get<ProductDetail>(`/products/${id}`)
  return response.data
}

export async function listCategories(): Promise<Category[]> {
  const response = await api.get<Category[]>('/categories')
  return response.data
}