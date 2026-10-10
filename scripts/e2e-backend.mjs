import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const backend = path.join(repository, 'backend')
const windows = process.platform === 'win32'
const child = spawn(windows ? 'cmd.exe' : './mvnw',
  windows ? ['/d', '/s', '/c', 'mvnw.cmd spring-boot:run'] : ['spring-boot:run'], {
  cwd: backend,
  windowsHide: true,
  stdio: 'inherit',
  env: {
    ...process.env,
    DB_URL: 'jdbc:postgresql://127.0.0.1:5434/online_shop_e2e',
    DB_USERNAME: 'online_shop_e2e',
    DB_PASSWORD: 'e2e_local_only',
    BACKEND_PORT: '8081',
    MAIL_HOST: '127.0.0.1',
    MAIL_PORT: '1026',
    MAIL_USERNAME: '',
    MAIL_PASSWORD: '',
    MAIL_FROM: 'no-reply@online-shop.local',
    MAIL_SMTP_AUTH: 'false',
    MAIL_SMTP_STARTTLS: 'false',
    FRONTEND_BASE_URL: 'http://127.0.0.1:5174',
    CORS_ALLOWED_ORIGINS: 'http://127.0.0.1:5174',
  },
})

function stop() {
  if (!child.pid || child.exitCode !== null) return
  if (process.platform === 'win32') {
    spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true })
  } else child.kill('SIGTERM')
}

process.on('SIGINT', stop)
process.on('SIGTERM', stop)
child.on('exit', (code) => { process.exitCode = code ?? 1 })
