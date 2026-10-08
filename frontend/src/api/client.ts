import axios from 'axios'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 10000,
})

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('online-shop-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export function setAccessToken(token: string) {
  sessionStorage.setItem('online-shop-token', token)
}

export function clearAccessToken() {
  sessionStorage.removeItem('online-shop-token')
}

export function hasAccessToken() {
  return Boolean(sessionStorage.getItem('online-shop-token'))
}
