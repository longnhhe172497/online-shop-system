import { cleanup, render, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import GoogleButton from './GoogleButton'
import { getGoogleConfig } from './api/auth'

vi.mock('./api/auth', () => ({ getGoogleConfig: vi.fn() }))

describe('GoogleButton', () => {
  afterEach(() => { cleanup(); delete window.google; vi.clearAllMocks() })

  it('initializes Google Identity once and delivers credentials to the current screen', async () => {
    let deliver: (response: { credential: string }) => void = () => {}
    const initialize = vi.fn((options: { callback: (response: { credential: string }) => void }) => {
      deliver = options.callback
    })
    const renderButton = vi.fn((host: HTMLElement) => { host.appendChild(document.createElement('button')) })
    window.google = { accounts: { id: { initialize, renderButton } } }
    vi.mocked(getGoogleConfig).mockResolvedValue({ enabled: true,
      clientId: 'local-test.apps.googleusercontent.com' })
    const firstHandler = vi.fn()
    const first = render(<GoogleButton onCredential={firstHandler} />)
    await waitFor(() => expect(renderButton).toHaveBeenCalledTimes(1))
    first.unmount()

    const secondHandler = vi.fn()
    render(<GoogleButton onCredential={secondHandler} />)
    await waitFor(() => expect(renderButton).toHaveBeenCalledTimes(2))
    expect(initialize).toHaveBeenCalledTimes(1)
    deliver({ credential: 'new-screen-token' })
    expect(secondHandler).toHaveBeenCalledWith('new-screen-token')
    expect(firstHandler).not.toHaveBeenCalled()
  })
})
