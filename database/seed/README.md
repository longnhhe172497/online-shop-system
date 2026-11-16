# FORME demo data

`dev_full_demo.sql` rebuilds the local application data with realistic, connected records for every implemented module. It is intentionally separate from Flyway because demo data must never be installed in production.

For a smaller, non-destructive product-only dataset, use `dev_catalog.sql` instead. The instructions below apply to the complete demo dataset.

## Run

From the repository root in PowerShell:

```powershell
.\scripts\seed-demo.ps1 -Force
```

The command starts PostgreSQL and MailHog when necessary, executes the seed in one transaction and then runs `verify_full_demo.sql`. It can be run again whenever a clean demo baseline is needed.

> This is destructive for local application data. Flyway history and the database schema are preserved.

## Login accounts

All seeded accounts use the password `Demo@123456`.

| Purpose | Email |
| --- | --- |
| Primary Admin and real Google-link candidate | `longnhhe172497@fpt.edu.vn` |
| Backup Admin | `admin@forme.test` |
| Manager | `manager@forme.test` |
| Support | `support@forme.test` |
| Warehouse | `warehouse@forme.test` |
| Delivery Staff 1 | `delivery1@forme.test` |
| Delivery Staff 2 | `delivery2@forme.test` |
| Customer with populated cart/orders | `lan.customer@forme.test` |
| Second customer | `minh.customer@forme.test` |
| Customer requiring email MFA | `mfa.email@forme.test` |
| Pending verification | `pending@forme.test` |
| Locked account | `locked@forme.test` |
| Inactive account | `inactive@forme.test` |

The email-MFA account sends its login code through the configured mail service. With the default local environment, open MailHog at `http://localhost:8025`.

## Known one-time links

These deterministic tokens exist only to exercise local UI flows:

| Flow | URL |
| --- | --- |
| Verify pending account | `http://localhost:5173/verify-email?token=demo-verify-token` |
| Reset Lan's password | `http://localhost:5173/reset-password?token=demo-password-reset-token` |
| Accept Support invitation | `http://localhost:5173/accept-invitation?token=demo-staff-invitation-token` |
| Confirm Lan's email change | `http://localhost:5173/email-change/confirm?token=demo-email-change-token` |

Each token is single-use. Rerun the seed to restore it.

## What is covered

- Active accounts for all six roles, two Admin accounts and pending/locked/inactive states.
- Multiple addresses, customer carts, a guest cart and realistic product/catalog data.
- Active, draft, inactive and expired vouchers.
- COD and simulated-QR orders in every supported status.
- Pending, failed, paid, unpaid and cancelled payment attempts.
- Active, consumed and released stock reservations plus inventory history.
- Ready, assigned, in-transit, delivered, failed and returned shipments.
- Return/refund requests ready for decision, inspection and refund completion.
- Open, in-progress, resolved and closed support tickets with conversations.
- Visible/hidden/replied reviews, staff daily reports, sessions and audit history.
- A delivered order named `DEMO-RETURN-ELIGIBLE` with no return request, so a customer can create a new request manually.

TOTP enrollment and Google sign-in are intentionally not forged in SQL. They depend on the application's encryption key and a genuine Google credential. Sign in to `longnhhe172497@fpt.edu.vn` with the demo password to link the real matching Google account, or enable TOTP from Account Security. All required account data is present for both workflows.

## Automated test note

This dataset is intended for manual UI/API testing. Several existing database integration tests assert exact record counts and therefore expect an empty application database. Run the full backend integration suite before loading demo data, or rerun this seed after the suite. Backend unit tests and frontend tests do not require an empty database.
