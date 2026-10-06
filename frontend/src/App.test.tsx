import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { listProducts } from './api/products'

vi.mock('./api/products', () => ({ listProducts: vi.fn() }))

describe('App', () => {
  beforeEach(() => vi.resetAllMocks())

  it('renders products returned by the API client', async () => {
    vi.mocked(listProducts).mockResolvedValue({
      items: [{ id: 1, sku: 'TEST-1', name: 'Sample product', description: null,
        imageUrl: null, price: 120000, categoryName: 'Test category', availableQuantity: 5 }],
      page: 0, size: 20, totalItems: 1, totalPages: 1,
    })
    render(<App />)
    expect(await screen.findByText('Sample product')).toBeInTheDocument()
    expect(screen.getByText(/120.000/)).toBeInTheDocument()
  })

  it('shows an empty state when there are no active products', async () => {
    vi.mocked(listProducts).mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })
    render(<App />)
    expect(await screen.findByText(/No active products yet/)).toBeInTheDocument()
  })
})
