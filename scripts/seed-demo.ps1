param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$seedFile = Join-Path $repoRoot 'database\seed\dev_full_demo.sql'
$verifyFile = Join-Path $repoRoot 'database\seed\verify_full_demo.sql'
$containerName = 'online-shop-postgres'

if (-not $Force) {
    throw 'Seed này sẽ thay thế toàn bộ dữ liệu local. Chạy lại với: .\scripts\seed-demo.ps1 -Force'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Không tìm thấy Docker CLI. Hãy cài hoặc mở Docker Desktop.'
}
if (-not (Test-Path -LiteralPath $seedFile) -or -not (Test-Path -LiteralPath $verifyFile)) {
    throw 'Không tìm thấy file seed hoặc file kiểm tra.'
}

Write-Host 'Đang bảo đảm PostgreSQL và MailHog đã chạy...' -ForegroundColor Yellow
docker compose --project-directory $repoRoot up -d postgres mailhog
if ($LASTEXITCODE -ne 0) { throw 'Không thể khởi động PostgreSQL hoặc MailHog.' }

$ready = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    docker exec $containerName pg_isready -U online_shop -d online_shop *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'PostgreSQL chưa sẵn sàng sau 30 giây.' }

Write-Host 'Đang tạo dữ liệu demo đầy đủ...' -ForegroundColor Cyan
Get-Content -LiteralPath $seedFile -Raw -Encoding UTF8 |
    docker exec -i $containerName psql -X -v ON_ERROR_STOP=1 -U online_shop -d online_shop
if ($LASTEXITCODE -ne 0) { throw 'Seed thất bại. Giao dịch đã được rollback.' }

Write-Host 'Đang kiểm tra tính đầy đủ và nhất quán...' -ForegroundColor Cyan
Get-Content -LiteralPath $verifyFile -Raw -Encoding UTF8 |
    docker exec -i $containerName psql -X -v ON_ERROR_STOP=1 -U online_shop -d online_shop
if ($LASTEXITCODE -ne 0) { throw 'Seed đã chạy nhưng bước kiểm tra thất bại.' }

Write-Host ''
Write-Host 'Dữ liệu demo đã sẵn sàng.' -ForegroundColor Green
Write-Host 'Mật khẩu chung: Demo@123456'
Write-Host 'Admin: longnhhe172497@fpt.edu.vn'
Write-Host 'Manager: manager@forme.test'
Write-Host 'Support: support@forme.test'
Write-Host 'Warehouse: warehouse@forme.test'
Write-Host 'Delivery: delivery1@forme.test hoặc delivery2@forme.test'
Write-Host 'Customer: lan.customer@forme.test hoặc minh.customer@forme.test'
