import { api } from './client'
import { announceCartCount } from '../cartEvents'

export type CartWarning = 'PRODUCT_UNAVAILABLE' | 'INSUFFICIENT_STOCK'

export interface CartLine {
  id: number
  productId: number
  sku: string
  name: string
  imageUrl: string | null
  unitPrice: number
  quantity: number
  availableQuantity: number
  lineTotal: number
  warning: CartWarning | null
}

export interface Cart {
  items: CartLine[]
  itemCount: number
  subtotal: number
}

function changed(cart: Cart) {
  announceCartCount(cart.itemCount)
  return cart
}

export async function getCart(): Promise<Cart> {
  const { data } = await api.get<Cart>('/cart')
  return data
}

export async function addCartItem(productId: number, quantity: number): Promise<Cart> {
  const { data } = await api.post<Cart>('/cart/items', { productId, quantity })
  return changed(data)
}

export async function updateCartItem(itemId: number, quantity: number): Promise<Cart> {
  const { data } = await api.patch<Cart>(`/cart/items/${itemId}`, { quantity })
  return changed(data)
}

export async function removeCartItem(itemId: number): Promise<Cart> {
  const { data } = await api.delete<Cart>(`/cart/items/${itemId}`)
  return changed(data)
}
