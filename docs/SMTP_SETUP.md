# Send real verification email (local backend)

The backend defaults to MailHog. To send from a real mailbox, obtain your mail provider's SMTP host, port, security mode and an app-specific password or SMTP credential. Do **not** use a normal account password if your provider requires an app password. Never commit the credential or send it in chat.

## One-time local configuration

From the repository root, copy the template once, then edit the ignored `.env` file locally:

```powershell
Copy-Item .env.example .env
```

For Gmail, set `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME` and `MAIL_FROM` to the sending account, `MAIL_PASSWORD` to an app password created by that same account, and both `MAIL_SMTP_AUTH` and `MAIL_SMTP_STARTTLS` to `true`. Do not change the database variables already in `.env`. The backend imports this file automatically **when started from the `backend` directory**; no PowerShell environment-variable commands are needed on later runs. Keep PostgreSQL running with `docker compose up -d postgres`.

The real `.env` is ignored by Git. Never paste it into chat or commit it; `.env.example` contains only safe defaults and belongs in Git. Each team member needs their own local `.env` to send real email. Without Gmail credentials, the template routes email to MailHog at `http://localhost:8025`.

Port 587 with STARTTLS is a common pattern, **not** a universal setting. Use the host, port and authentication method published by your own provider. This application currently supports SMTP AUTH with optional STARTTLS; providers requiring OAuth-only SMTP or implicit TLS on port 465 need additional integration and should not be configured by guessing.

`MAIL_FROM` should normally equal the authenticated mailbox, unless the provider explicitly allows a verified alias. `FRONTEND_BASE_URL` must point to the frontend as seen by the recipient. `localhost` works only when the recipient opens the email on the same computer as the frontend. For another device on the same LAN, use an accessible LAN address and start Vite with `npm run dev -- --host 0.0.0.0`; firewall rules may also need adjustment. For people on the Internet, a local-only frontend cannot serve the link: deploy it or use an approved secure tunnel and set its HTTPS URL here.

People on the Internet must also be able to reach the backend API. Once both addresses exist, set `FRONTEND_BASE_URL` to the public frontend origin, set `CORS_ALLOWED_ORIGINS` to that same origin (comma-separate additional trusted origins if needed), and set `VITE_API_BASE_URL` to the public backend URL ending in `/api` **before building or starting the frontend**. A frontend hosted publicly but still configured to call `http://localhost:8080/api` will call the visitor's own computer and verification will fail. Use HTTPS for both public endpoints.

If you do not yet have public HTTPS addresses, keep verification on the same local computer. A real email can still be sent, but its `localhost` link will not work on a different device.

## Verify the flow

1. Start PostgreSQL, backend and frontend. Register using an email inbox you control at `/auth`.
2. Check that inbox (including spam) for the verification message sent by `MAIL_FROM`.
3. Open the link within 24 hours. It should display a verification success page.
4. Sign in. An unverified account must not be able to sign in.

If SMTP rejects the message, registration fails and the database transaction rolls back, so the same email can be retried after fixing the configuration. Common causes are an incorrect app password, wrong SMTP host/port, blocked SMTP access, or a From address not permitted by the provider. The backend's error log can help diagnose transport failures; do not share lines containing secrets or verification links.

Automated tests mock the mail sender and do not send external messages.
