export const CART_EVENT = 'forme:cart'

/** Tells the header badge the new item count after a cart change. */
export function announceCartCount(count: number) {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent<number>(CART_EVENT, { detail: count }))
}
