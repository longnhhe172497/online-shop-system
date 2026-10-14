import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SessionIdleNotice from './SessionIdleNotice'
import { getMe } from './api/auth'

vi.mock('./api/auth', () => ({ getMe: vi.fn(), logout: vi.fn() }))

describe('SessionIdleNotice', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-11T00:00:00Z'))
    vi.mocked(getMe).mockResolvedValue({ id: 1, email: 'user@example.com',
      fullName: 'Người dùng', role: 'CUSTOMER' })
  })
  afterEach(() => { cleanup(); vi.useRealTimers(); vi.resetAllMocks() })

  it('warns before idle expiry and renews only after the server accepts the session', async () => {
    render(<MemoryRouter initialEntries={['/me']}><SessionIdleNotice /></MemoryRouter>)
    await act(async () => { await Promise.resolve() })
    expect(getMe).toHaveBeenCalledTimes(1)
    await act(async () => { vi.advanceTimersByTime(25 * 60 * 1000) })
    expect(screen.getByRole('alertdialog', { name: 'Phiên sắp hết hạn' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục phiên' }))
    await act(async () => { await Promise.resolve() })
    expect(getMe).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })
})
