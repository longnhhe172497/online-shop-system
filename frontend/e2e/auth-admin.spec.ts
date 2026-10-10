import { expect, test, type APIRequestContext } from '@playwright/test'
import pg from 'pg'

type MailhogMessage = { To: { Mailbox: string; Domain: string }[]; Content: { Body: string } }

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

test('register, verify email, sign in with MFA, and invite internal staff', async ({ page, request }) => {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 100_000)}`
  const adminEmail = `admin-${suffix}@example.test`
  const staffEmail = `staff-${suffix}@example.test`
  const password = 'LocalE2ePassword123!'

  await page.goto('/register')
  await page.getByLabel('HỌ VÀ TÊN').fill('Quản trị E2E')
  await page.getByLabel('ĐỊA CHỈ EMAIL').fill(adminEmail)
  await page.getByLabel('MẬT KHẨU', { exact: true }).fill(password)
  await page.getByLabel('NHẬP LẠI MẬT KHẨU').fill(password)
  await page.getByRole('button', { name: 'TẠO TÀI KHOẢN' }).click()
  await expect(page.getByRole('heading', { name: 'Kiểm tra email' })).toBeVisible()
  const verification = await latestMail(request, adminEmail, '/verify-email?token=')
  await page.goto(linkFrom(verification, 'verify-email'))
  await page.getByRole('button', { name: 'XÁC MINH EMAIL' }).click()
  await expect(page.getByText('Email đã được xác minh. Bạn có thể đăng nhập.')).toBeVisible()

  // The E2E stack has its own DB on port 5434. Promote only this unique test account.
  const database = new pg.Client({ host: '127.0.0.1', port: 5434, database: 'online_shop_e2e',
    user: 'online_shop_e2e', password: 'e2e_local_only' })
  await database.connect()
  try {
    const result = await database.query("UPDATE users SET role='ADMIN' WHERE email=$1 RETURNING id", [adminEmail])
    expect(result.rowCount).toBe(1)
  } finally { await database.end() }

  await page.goto('/auth')
  await page.getByLabel('ĐỊA CHỈ EMAIL').fill(adminEmail)
  await page.getByLabel('MẬT KHẨU', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'ĐĂNG NHẬP' }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  await page.goto('/me/security')
  await page.getByRole('button', { name: 'Bật xác minh hai bước' }).click()
  const enrollment = await latestMail(request, adminEmail, 'Mã xác minh FORME của bạn:')
  const enrollmentCode = enrollment.match(/Mã xác minh FORME của bạn: (\d{6})/)?.[1]
  expect(enrollmentCode).toBeTruthy()
  await page.getByLabel('MÃ SÁU CHỮ SỐ').fill(enrollmentCode!)
  await page.getByRole('button', { name: 'Xác nhận và bật' }).click()
  await expect(page.getByText('Mã khôi phục — chỉ hiển thị lần này')).toBeVisible()

  await page.goto('/auth')
  await page.getByLabel('ĐỊA CHỈ EMAIL').fill(adminEmail)
  await page.getByLabel('MẬT KHẨU', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'ĐĂNG NHẬP' }).click()
  await expect(page.getByRole('heading', { name: 'Xác minh đăng nhập' })).toBeVisible()
  const loginMail = await latestMail(request, adminEmail, 'Mã xác minh FORME của bạn:', enrollment)
  const loginCode = loginMail.match(/Mã xác minh FORME của bạn: (\d{6})/)?.[1]
  expect(loginCode).toBeTruthy()
  await page.getByLabel('MÃ XÁC MINH HOẶC MÃ KHÔI PHỤC').fill(loginCode!)
  await page.getByRole('button', { name: 'XÁC MINH' }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()

  await page.goto('/internal/invitations')
  await page.getByRole('button', { name: '＋ Gửi lời mời' }).click()
  const dialog = page.getByRole('dialog', { name: 'Gửi lời mời' })
  await dialog.getByLabel('HỌ VÀ TÊN').fill('Nhân viên E2E')
  await dialog.getByLabel('ĐỊA CHỈ EMAIL').fill(staffEmail)
  await dialog.getByLabel('VAI TRÒ').selectOption('WAREHOUSE')
  await dialog.getByRole('button', { name: 'Gửi lời mời' }).click()
  await expect(page.getByText('Đã gửi lời mời qua email. Người nhận sẽ tự đặt mật khẩu.')).toBeVisible()
  const invitation = await latestMail(request, staffEmail, '/accept-invitation?token=')
  await page.goto(linkFrom(invitation, 'accept-invitation'))
  await page.getByLabel('MẬT KHẨU', { exact: true }).fill(password)
  await page.getByLabel('NHẬP LẠI MẬT KHẨU').fill(password)
  await page.getByRole('button', { name: 'KÍCH HOẠT TÀI KHOẢN' }).click()
  await expect(page.getByText('Tài khoản đã được kích hoạt. Bạn có thể đăng nhập.')).toBeVisible()
  await page.goto('/auth')
  await page.getByLabel('ĐỊA CHỈ EMAIL').fill(staffEmail)
  await page.getByLabel('MẬT KHẨU', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'ĐĂNG NHẬP' }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  await expect(page.getByText('Nhân viên kho').first()).toBeVisible()
})
