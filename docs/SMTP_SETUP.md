# Send real verification email (local backend)

The backend defaults to MailHog. To send from a real mailbox, obtain your mail provider's SMTP host, port, security mode and an app-specific password or SMTP credential. Do **not** use a normal account password if your provider requires an app password. Never commit the credential or send it in chat.

## Configure the backend terminal

In the same PowerShell window where you will start the backend, set these values, replacing the examples with your provider's documented settings:

```powershell
$env:MAIL_HOST = 'smtp.your-provider.example'
$env:MAIL_PORT = '587'
$env:MAIL_USERNAME = 'your-address@example.com'
$env:MAIL_PASSWORD = 'your-app-password'
$env:MAIL_FROM = 'your-address@example.com'
$env:MAIL_SMTP_AUTH = 'true'
$env:MAIL_SMTP_STARTTLS = 'true'
$env:FRONTEND_BASE_URL = 'http://localhost:5173'
cd backend
.\mvnw.cmd spring-boot:run
```

These PowerShell values apply only to that terminal session. Enter the secret locally; do not paste real credentials into repository files, screenshots, logs, GitHub issues or pull requests. Keep PostgreSQL running with `docker compose up -d postgres`. MailHog does not need to run when real SMTP is selected.

Port 587 with STARTTLS is a common pattern, **not** a universal setting. Use the host, port and authentication method published by your own provider. This application currently supports SMTP AUTH with optional STARTTLS; providers requiring OAuth-only SMTP or implicit TLS on port 465 need additional integration and should not be configured by guessing.

`MAIL_FROM` should normally equal the authenticated mailbox, unless the provider explicitly allows a verified alias. `FRONTEND_BASE_URL` must point to the frontend as seen by the recipient. `localhost` works only when the recipient opens the email on the same computer as the frontend. For another device on the same LAN, use an accessible LAN address and start Vite with `npm run dev -- --host 0.0.0.0`; firewall rules may also need adjustment. For people on the Internet, a local-only frontend cannot serve the link: deploy it or use an approved secure tunnel and set its HTTPS URL here.

People on the Internet must also be able to reach the backend API. Once both addresses exist, set `FRONTEND_BASE_URL` to the public frontend origin, set `CORS_ALLOWED_ORIGINS` to that same origin (comma-separate additional trusted origins if needed), and set `VITE_API_BASE_URL` to the public backend URL ending in `/api` **before building or starting the frontend**. A frontend hosted publicly but still configured to call `http://localhost:8080/api` will call the visitor's own computer and verification will fail. Use HTTPS for both public endpoints.

If you do not yet have provider SMTP settings and public HTTPS addresses, stop at local MailHog testing. The code is configurable, but end-to-end Internet verification cannot be validated or made operational until those details exist.

## Verify the flow

1. Start PostgreSQL, backend and frontend. Register using an email inbox you control at `/auth`.
2. Check that inbox (including spam) for the verification message sent by `MAIL_FROM`.
3. Open the link within 24 hours. It should display a verification success page.
4. Sign in. An unverified account must not be able to sign in.

If SMTP rejects the message, registration fails and the database transaction rolls back, so the same email can be retried after fixing the configuration. Common causes are an incorrect app password, wrong SMTP host/port, blocked SMTP access, or a From address not permitted by the provider. The backend's error log can help diagnose transport failures; do not share lines containing secrets or verification links.

For team development, leave the SMTP environment variables unset and use MailHog at `http://localhost:8025`. Automated tests mock the mail sender and do not send external messages.
