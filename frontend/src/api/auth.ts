import { api } from './client'

export interface AuthUser {
  id: number
  email: string
  fullName: string
  role: string
}

export interface LoginResult { user: AuthUser | null; mfaRequired: boolean;
  challengeToken: string | null; mfaMethod?: 'EMAIL' | 'TOTP' | null }

export async function registerAccount(input: {
  fullName: string
  email: string
  password: string
  confirmPassword: string
  phone?: string
}) {
  const { data } = await api.post<{ userId: number; verificationRequired: boolean; emailSent: boolean }>('/auth/register', input)
  return data
}

export async function verifyAccount(token: string) {
  const { data } = await api.post<{ verified: boolean }>('/auth/verify', { token })
  return data
}

export async function requestPasswordReset(email: string) {
  await api.post('/auth/password-reset/request', { email })
}

export async function confirmPasswordReset(input: {
  token: string
  newPassword: string
  confirmPassword: string
}) {
  const { data } = await api.post<{ reset: boolean }>('/auth/password-reset/confirm', input)
  return data
}

export async function login(input: { email: string; password: string }) {
  const { data } = await api.post<LoginResult>('/auth/browser-login', input)
  return data
}

export async function verifyMfa(challengeToken: string, code: string) {
  const { data } = await api.post<LoginResult>('/auth/browser-mfa/verify', { challengeToken, code })
  return data
}

export async function resendVerification(email: string) {
  await api.post('/auth/verification/resend', { email })
}

export async function logout() {
  await api.post('/auth/logout')
}

export async function getMe() {
  const { data } = await api.get<AuthUser>('/me')
  return data
}
