import { expect, it } from 'vitest'
import { getUiError } from './uiError'

it('explains an expired authenticator setup and what to do next', () => {
  expect(getUiError({ isAxiosError: true, response: { status: 400,
    data: { code: 'INVALID_TOTP_ENROLLMENT' } } })).toBe(
    'Thời gian thiết lập ứng dụng xác thực đã hết. Hãy bắt đầu thiết lập lại.')
})

it('explains an expired MFA challenge without assuming the user is logging in', () => {
  expect(getUiError({ isAxiosError: true, response: { status: 400,
    data: { code: 'INVALID_MFA_CHALLENGE' } } })).toBe(
    'Bước xác minh đã hết hạn. Hãy bắt đầu lại thao tác xác minh.')
})
