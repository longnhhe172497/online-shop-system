import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import pg from 'pg'

type MailhogMessage = { To: { Mailbox: string; Domain: string }[]; Content: { Body: string } }

const password = 'LocalE2ePassword123!'

function decodeMail(body: string) {
  const unfolded = body.replace(/=\r?\n/g, '')
  if (!/=[0-9A-F]{2}/i.test(unfolded)) return unfolded
  const latin1 = unfolded.replace(/=([0-9A-F]{2})/gi, (_, hex: string) =>
    String.fromCharCode(Number.parseInt(hex, 16)))
  return Buffer.from(latin1, 'binary').toString('utf8')
}

async function latestMail(request: APIRequestContext, email: string, marker: string, exclude = '') {
  let matching = ''
  await expect.poll(async () => {
    const response = await request.get('http://127.0.0.1:8026/api/v2/messages')
    if (!response.ok()) return false
    const payload = await response.json() as { items?: MailhogMessage[] }
    const messages = (payload.items ?? []).filter((item) => item.To.some((recipient) =>
      `${recipient.Mailbox}@${recipient.Domain}`.toLowerCase() === email.toLowerCase()))
    matching = messages.map((item) => decodeMail(item.Content.Body))
      .find((body) => body.includes(marker) && body !== exclude) ?? ''
    return matching.length > 0
  }, { timeout: 20_000 }).toBe(true)
  return matching
}

function linkFrom(body: string, path: string) {
  const match = body.match(new RegExp(`http://127\\.0\\.0\\.1:5174/${path}\\?token=[^\\s]+`))
  if (!match) throw new Error(`Expected ${path} link in MailHog message`)
  return match[0]
}

async function registerAndVerify(page: Page, request: APIRequestContext, email: string) {
  await page.goto('/register')
  await page.getByLabel('HỌ VÀ TÊN').fill('Khách hàng E2E')
  await page.getByLabel('ĐỊA CHỈ EMAIL', { exact: true }).fill(email)
  await page.getByLabel('MẬT KHẨU', { exact: true }).fill(password)
  await page.getByLabel('NHẬP LẠI MẬT KHẨU').fill(password)
  await page.getByRole('button', { name: 'TẠO TÀI KHOẢN' }).click()
  await expect(page.getByRole('heading', { name: 'Kiểm tra email' })).toBeVisible()
  const verification = await latestMail(request, email, '/verify-email?token=')
  await page.goto(linkFrom(verification, 'verify-email'))
  await page.getByRole('button', { name: 'XÁC MINH EMAIL' }).click()
  await expect(page.getByText('Email đã được xác minh. Bạn có thể đăng nhập.')).toBeVisible()
}

async function signIn(page: Page, email: string, signInPassword = password) {
  await page.goto('/auth')
  await page.getByLabel('ĐỊA CHỈ EMAIL', { exact: true }).fill(email)
  await page.getByLabel('MẬT KHẨU', { exact: true }).fill(signInPassword)
  await page.getByRole('button', { name: 'ĐĂNG NHẬP', exact: true }).click()
}

test('request a password reset, consume the one-time link, and sign in with the new password',
  async ({ page, request }) => {
    const email = `reset-${Date.now()}-${Math.floor(Math.random() * 100_000)}@example.test`
    const newPassword = 'UpdatedE2ePassword456!'
    await registerAndVerify(page, request, email)

    await page.goto('/forgot-password')
    await page.getByLabel('ĐỊA CHỈ EMAIL', { exact: true }).fill(email)
    await page.getByRole('button', { name: 'GỬI LIÊN KẾT' }).click()
    await expect(page.getByText(/Nếu email thuộc một tài khoản đang hoạt động/)).toBeVisible()
    const resetMessage = await latestMail(request, email, '/reset-password?token=')
    await page.goto(linkFrom(resetMessage, 'reset-password'))
    await page.getByLabel('MẬT KHẨU MỚI', { exact: true }).fill(newPassword)
    await page.getByLabel('NHẬP LẠI MẬT KHẨU MỚI').fill(newPassword)
    await page.getByRole('button', { name: 'ĐẶT LẠI MẬT KHẨU' }).click()
    await expect(page.getByText('Mật khẩu đã được cập nhật. Hãy đăng nhập bằng mật khẩu mới.')).toBeVisible()

    await signIn(page, email, newPassword)
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { name: 'Sản phẩm', exact: true })).toBeVisible()
  })

test('resend verification, sign out, and reject an account that an administrator deactivated',
  async ({ page, request }) => {
    const email = `status-${Date.now()}-${Math.floor(Math.random() * 100_000)}@example.test`
    await page.goto('/register')
    await page.getByLabel('HỌ VÀ TÊN').fill('Khách hàng trạng thái E2E')
    await page.getByLabel('ĐỊA CHỈ EMAIL', { exact: true }).fill(email)
    await page.getByLabel('MẬT KHẨU', { exact: true }).fill(password)
    await page.getByLabel('NHẬP LẠI MẬT KHẨU').fill(password)
    await page.getByRole('button', { name: 'TẠO TÀI KHOẢN' }).click()
    const originalVerification = await latestMail(request, email, '/verify-email?token=')

    // The product intentionally throttles resend for five minutes. Move only this isolated
    // test account's token outside that window instead of slowing the suite down for five minutes.
    const cooldownDatabase = new pg.Client({ host: '127.0.0.1', port: 5434, database: 'online_shop_e2e',
      user: 'online_shop_e2e', password: 'e2e_local_only' })
    await cooldownDatabase.connect()
    try {
      await cooldownDatabase.query(`UPDATE auth_tokens SET created_at=now() - interval '6 minutes'
        WHERE user_id=(SELECT id FROM users WHERE email=$1) AND purpose='EMAIL_VERIFICATION'`, [email])
    } finally { await cooldownDatabase.end() }

    await page.goto('/resend-verification')
    await page.getByLabel('ĐỊA CHỈ EMAIL', { exact: true }).fill(email)
    await page.getByRole('button', { name: 'GỬI LIÊN KẾT' }).click()
    await expect(page.getByText('Yêu cầu đã được tiếp nhận. Hãy kiểm tra hộp thư và thư rác.')).toBeVisible()
    const replacementVerification = await latestMail(request, email, '/verify-email?token=', originalVerification)
    await page.goto(linkFrom(replacementVerification, 'verify-email'))
    await page.getByRole('button', { name: 'XÁC MINH EMAIL' }).click()
    await expect(page.getByText('Email đã được xác minh. Bạn có thể đăng nhập.')).toBeVisible()

    await signIn(page, email)
    await expect(page.getByRole('heading', { name: 'Sản phẩm', exact: true })).toBeVisible()
    await page.goto('/me')
    await page.getByRole('button', { name: 'Đăng xuất' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã đăng xuất.')

    const database = new pg.Client({ host: '127.0.0.1', port: 5434, database: 'online_shop_e2e',
      user: 'online_shop_e2e', password: 'e2e_local_only' })
    await database.connect()
    try {
      const result = await database.query("UPDATE users SET status='INACTIVE' WHERE email=$1", [email])
      expect(result.rowCount).toBe(1)
    } finally { await database.end() }

    await page.getByLabel('ĐỊA CHỈ EMAIL', { exact: true }).fill(email)
    await page.getByLabel('MẬT KHẨU', { exact: true }).fill(password)
    await page.getByRole('button', { name: 'ĐĂNG NHẬP', exact: true }).click()
    // The UI deliberately keeps the reason generic so an attacker cannot enumerate account status.
    await expect(page.getByRole('alert')).toHaveText('Email hoặc mật khẩu không đúng.')
  })
