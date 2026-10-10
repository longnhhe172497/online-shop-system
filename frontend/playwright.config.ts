import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  retries: 0,
  workers: 1,
  use: { ...devices['Desktop Chrome'], baseURL: 'http://127.0.0.1:5174', trace: 'retain-on-failure' },
  webServer: [
    { command: 'node ../scripts/e2e-backend.mjs', url: 'http://127.0.0.1:8081/api/health',
      timeout: 120_000, reuseExistingServer: false },
    { command: 'npm run dev -- --host 127.0.0.1 --port 5174 --strictPort',
      url: 'http://127.0.0.1:5174', timeout: 30_000, reuseExistingServer: false,
      env: { VITE_API_BASE_URL: 'http://127.0.0.1:8081/api' } },
  ],
})
