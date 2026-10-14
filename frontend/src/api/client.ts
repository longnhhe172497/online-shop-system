import axios from 'axios'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 10000,
  withCredentials: true,
})

// The browser session lives in an HttpOnly cookie. Obtain a fresh masked CSRF token
// for every write so no authentication secret has to be readable by JavaScript.
api.interceptors.request.use(async (config) => {
  const method = (config.method ?? 'get').toLowerCase()
  if (!['get', 'head', 'options'].includes(method)) {
    const { data } = await axios.get<{ token: string }>('/auth/csrf', {
      baseURL: api.defaults.baseURL, timeout: 10000, withCredentials: true,
    })
    config.headers.set('X-XSRF-TOKEN', data.token)
  }
  return config
})

api.interceptors.response.use((response) => {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('forme:session-activity'))
  return response
})
