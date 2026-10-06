import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 10000,
})

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
