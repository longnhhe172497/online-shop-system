import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import AccountSessionsPage from './AccountSessionsPage'
import { listSessions, revokeOtherSessions, revokeSession, type Session } from './api/profile'

vi.mock('./api/profile', () => ({ listSessions: vi.fn(), revokeSession: vi.fn(),
  revokeOtherSessions: vi.fn() }))
vi.mock('./SignOutButton', () => ({ default: () => null }))

const sessions: Session[] = [
  { id: 1, ipAddress: '127.0.0.1', userAgent: 'Chrome', createdAt: '2026-10-16T10:00:00Z',
    lastSeenAt: '2026-10-16T10:10:00Z', expiresAt: '2026-10-17T10:00:00Z', current: true },
  { id: 2, ipAddress: null, userAgent: null, createdAt: '2026-10-15T10:00:00Z',
    lastSeenAt: null, expiresAt: '2026-10-17T10:00:00Z', current: false },
]

beforeEach(() => { vi.resetAllMocks(); vi.mocked(listSessions).mockResolvedValue(sessions) })
afterEach(cleanup)

function openPage() { render(<MemoryRouter><AccountSessionsPage /></MemoryRouter>) }

it('shows current and other sessions and revokes only the selected other session', async () => {
  vi.mocked(revokeSession).mockResolvedValue()
  openPage()
  expect(await screen.findByText('Chrome')).toBeInTheDocument()
  expect(screen.getByText('Thiết bị này')).toBeInTheDocument()
  expect(screen.getByText('Trình duyệt không xác định')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Kết thúc phiên' }))
  await waitFor(() => expect(revokeSession).toHaveBeenCalledWith(2))
  expect(await screen.findByRole('status')).toHaveTextContent('Đã kết thúc phiên đăng nhập.')
  expect(screen.queryByText('Trình duyệt không xác định')).not.toBeInTheDocument()
  expect(screen.getByText('Chrome')).toBeInTheDocument()
})

it('signs out all other devices without removing the current session', async () => {
  vi.mocked(revokeOtherSessions).mockResolvedValue()
  openPage()
  fireEvent.click(await screen.findByRole('button', { name: 'Đăng xuất thiết bị khác' }))
  await waitFor(() => expect(revokeOtherSessions).toHaveBeenCalledOnce())
  expect(screen.getByText('Chrome')).toBeInTheDocument()
  expect(screen.queryByText('Trình duyệt không xác định')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Đăng xuất thiết bị khác' })).toBeDisabled()
})

it('shows a loading failure to the user', async () => {
  vi.mocked(listSessions).mockRejectedValue(new Error('Không tải được phiên'))
  openPage()
  expect(await screen.findByRole('alert')).toHaveTextContent('Không thể hoàn tất yêu cầu')
})
