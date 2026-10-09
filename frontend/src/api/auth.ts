import { api, clearAccessToken, setAccessToken } from './client'

export interface AuthUser {
  id: number
  email: string
  fullName: string
  role: string
}

export async function registerAccount(input: {
  fullName: string
  email: string
  password: string
  phone?: string
}) {
  const { data } = await api.post<{ userId: number; verificationRequired: boolean }>('/auth/register', input)
  return data
}

export async function verifyAccount(token: string) {
  const { data } = await api.post<{ verified: boolean }>('/auth/verify', { token })
  return data
}

export async function login(input: { email: string; password: string }) {
  const { data } = await api.post<{ accessToken: string; expiresAt: string; user: AuthUser }>('/auth/login', input)
  setAccessToken(data.accessToken)
  return data
}

export async function logout() {
  try {
    await api.post('/auth/logout')
  } finally {
    clearAccessToken()
  }
}

export async function getMe() {
  const { data } = await api.get<AuthUser>('/me')
  return data
}
