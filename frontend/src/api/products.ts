import { api } from './client'

export interface Product {
  id: number
  sku: string
  name: string
  description: string | null
  imageUrl: string | null
  price: number
  categoryName: string
  availableQuantity: number
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export async function listProducts(page = 0, size = 20): Promise<Page<Product>> {
  const response = await api.get<Page<Product>>('/products', { params: { page, size } })
  return response.data
}
