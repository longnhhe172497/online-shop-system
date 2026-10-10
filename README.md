# Online Shop System

Local-first online shop system developed by a five-member team.

## Technology baseline

- Java 21
- Spring Boot 4.1.1
- Maven Wrapper
- React 19
- TypeScript 6
- Vite 8
- Node.js 24 LTS
- PostgreSQL 18
- Docker Desktop with Docker Compose
- MailHog for local email testing
- Git and GitHub

## Prerequisites

- Git
- Eclipse Temurin JDK 21
- Node.js 24 LTS
- Docker Desktop with WSL 2
- IntelliJ IDEA for backend development
- Visual Studio Code for frontend development

Maven and PostgreSQL do not need to be installed separately.

## First-time setup

```powershell
Copy-Item .env.example .env
docker compose up -d
```

Run the backend in a new PowerShell window:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Run the frontend in another PowerShell window:

```powershell
cd frontend
npm ci
npm run dev
```

## Local URLs

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- MailHog: http://localhost:8025

To send real verification email from your own mailbox, follow [`docs/SMTP_SETUP.md`](docs/SMTP_SETUP.md). MailHog remains the safe default for local development.

## First Admin account (local only)

1. Register your own account in the frontend and verify its email first.
2. Open the local PostgreSQL console with `docker compose exec postgres psql -U online_shop -d online_shop` (adjust the user/database if you changed `.env`).
3. Replace the example email and run this once:

```sql
WITH promoted AS (
  UPDATE users SET role = 'ADMIN', updated_at = now()
  WHERE email = 'your-verified-email@example.com'
    AND status = 'ACTIVE' AND verified_at IS NOT NULL
  RETURNING id
)
INSERT INTO audit_logs(actor_id, action, entity_type, entity_id, details)
SELECT NULL, 'INITIAL_ADMIN_BOOTSTRAP', 'USER', id::text, '{}'::jsonb FROM promoted;
```

4. Sign out and sign in again, then open `http://localhost:5173/internal`.

The command does not create a shared or hard-coded Admin password. It only promotes your already verified account in your local database. The Admin screen can email invitations to staff; recipients set their own passwords. Each teammate's local database needs its own initial Admin if they want to test Admin functions.

## Internal workspace

After login, Admin, Manager, Support, Warehouse, and Delivery accounts enter `/internal`. Customer accounts enter the storefront. The internal sidebar shows only the sections currently authorized for that role: Admin sees account management, settings, and audit; other staff roles currently see a static overview until their business modules are implemented. The old `/admin` URL redirects to `/internal` for existing bookmarks. API access remains enforced by the backend, not only by the sidebar.

## Git workflow

- `main`: stable demonstration branch
- `develop`: team integration branch
- `feature/<issue-number>-<short-name>`: feature development
- `fix/<issue-number>-<short-name>`: bug fixes

All changes enter `develop` through pull requests. Do not commit secrets or push directly to `main`.
