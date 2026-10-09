import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { listProducts } from './api/products'
import { confirmPasswordReset, login, registerAccount, requestPasswordReset } from './api/auth'

vi.mock('./api/products', () => ({ listProducts: vi.fn() }))
vi.mock('./api/auth', () => ({
  login: vi.fn(), registerAccount: vi.fn(), getMe: vi.fn(), logout: vi.fn(), verifyAccount: vi.fn(),
  requestPasswordReset: vi.fn(), confirmPasswordReset: vi.fn(),
}))
vi.mock('./api/client', () => ({ hasAccessToken: () => false }))

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

describe('App', () => {
  beforeEach(() => vi.resetAllMocks())
  afterEach(() => cleanup())

  it('renders products returned by the API client', async () => {
    vi.mocked(listProducts).mockResolvedValue({
      items: [{ id: 1, sku: 'TEST-1', name: 'Sample product', description: null,
        imageUrl: null, price: 120000, categoryName: 'Test category', availableQuantity: 5 }],
      page: 0, size: 20, totalItems: 1, totalPages: 1,
    })
    renderAt('/')
    expect(await screen.findByText('Sample product')).toBeInTheDocument()
  })

  it('switches from sign in to registration', () => {
    renderAt('/auth')
    fireEvent.click(screen.getByRole('tab', { name: 'REGISTER' }))
    expect(screen.getByRole('heading', { name: 'Create account' })).toBeInTheDocument()
    expect(screen.getByLabelText('FULL NAME')).toBeInTheDocument()
    expect(screen.getByLabelText('CONFIRM PASSWORD')).toBeInTheDocument()
  })

  it('registers then directs the user to verify email', async () => {
    vi.mocked(registerAccount).mockResolvedValue({ userId: 1, verificationRequired: true })
    renderAt('/auth')
    fireEvent.click(screen.getByRole('tab', { name: 'REGISTER' }))
    fireEvent.change(screen.getByLabelText('FULL NAME'), { target: { value: 'Test User' } })
    fireEvent.change(screen.getByLabelText('EMAIL ADDRESS'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('PASSWORD'), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('CONFIRM PASSWORD'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: 'CREATE ACCOUNT' }))
    expect(await screen.findByText(/Account created/)).toBeInTheDocument()
    expect(registerAccount).toHaveBeenCalledWith({ fullName: 'Test User', email: 'test@example.com',
      password: 'password123', confirmPassword: 'password123' })
    expect(login).not.toHaveBeenCalled()
  })

  it('does not submit registration when passwords differ', () => {
    renderAt('/auth')
    fireEvent.click(screen.getByRole('tab', { name: 'REGISTER' }))
    fireEvent.change(screen.getByLabelText('FULL NAME'), { target: { value: 'Test User' } })
    fireEvent.change(screen.getByLabelText('EMAIL ADDRESS'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('PASSWORD'), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('CONFIRM PASSWORD'), { target: { value: 'different123' } })
    fireEvent.click(screen.getByRole('button', { name: 'CREATE ACCOUNT' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.')
    expect(registerAccount).not.toHaveBeenCalled()
  })

  it('requests a password reset without revealing whether the account exists', async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue(undefined)
    renderAt('/auth')
    fireEvent.click(screen.getByRole('link', { name: 'Forgot your password?' }))
    expect(screen.getByRole('heading', { name: 'Forgot password' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('EMAIL ADDRESS'), { target: { value: 'test@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'SEND RESET LINK' }))
    expect(await screen.findByText(/If an active account exists/)).toBeInTheDocument()
    expect(requestPasswordReset).toHaveBeenCalledWith('test@example.com')
  })

  it('requires matching passwords before confirming a reset', async () => {
    vi.mocked(confirmPasswordReset).mockResolvedValue({ reset: true })
    renderAt('/reset-password?token=test-token')
    fireEvent.change(screen.getByLabelText('NEW PASSWORD'), { target: { value: 'NewPassword123' } })
    fireEvent.change(screen.getByLabelText('CONFIRM NEW PASSWORD'), { target: { value: 'Different123' } })
    fireEvent.click(screen.getByRole('button', { name: 'SET NEW PASSWORD' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.')
    expect(confirmPasswordReset).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('CONFIRM NEW PASSWORD'), { target: { value: 'NewPassword123' } })
    fireEvent.click(screen.getByRole('button', { name: 'SET NEW PASSWORD' }))
    expect(await screen.findByText(/Password updated/)).toBeInTheDocument()
    expect(confirmPasswordReset).toHaveBeenCalledWith({ token: 'test-token',
      newPassword: 'NewPassword123', confirmPassword: 'NewPassword123' })
  })
})
