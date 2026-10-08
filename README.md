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

## Git workflow

- `main`: stable demonstration branch
- `develop`: team integration branch
- `feature/<issue-number>-<short-name>`: feature development
- `fix/<issue-number>-<short-name>`: bug fixes

All changes enter `develop` through pull requests. Do not commit secrets or push directly to `main`.
