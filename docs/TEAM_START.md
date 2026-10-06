# Team start guide — Online Shop System

This repository is the **new Online Shop System**, unrelated to the earlier OSM&SWS project. Work against the `develop` branch and the SRS for this repository only.

## What is ready now

- PostgreSQL 18 via Docker Compose; Flyway V1/V2 creates the 26-table B2C schema and V3 adds login sessions.
- Spring Boot backend with a running health endpoint, public paginated product endpoint, local CORS, validation errors, and Swagger UI.
- React frontend with one real API client and a product-list page. The page is empty until an active product is created.
- CI tests backend against PostgreSQL and runs frontend lint, build and tests on pull requests.
- [`API_CONTRACT.md`](API_CONTRACT.md) reserves route names, roles and basic payload fields. [`openapi.yaml`](../openapi.yaml) describes **only implemented routes**. Planned routes must not be mistaken for working APIs.

Authentication is **not yet implemented**. The existing SecurityConfig protects non-public routes, but its generated development password is not the project's login solution. Do not build feature code around that password.

## First day on each machine

1. Install Git, JDK 21, Node 24, Docker Desktop and the editors from the team setup guide.
2. Accept the GitHub invitation, clone the repository, then run `git switch develop` and `git pull origin develop`.
3. From the repository root run `Copy-Item .env.example .env` (once) and `docker compose up -d`.
4. From `backend` run `.\mvnw.cmd test`, then `.\mvnw.cmd spring-boot:run` in a separate terminal.
5. From `frontend` run `npm ci`, `npm run lint`, `npm run test`, then `npm run dev`.
6. Check `http://localhost:8080/api/health`, `http://localhost:8080/api/products`, `http://localhost:8080/swagger-ui/index.html`, and `http://localhost:5173`.

No one should edit an applied Flyway migration. Use a new versioned migration for each schema change.

## Five non-overlapping workstreams

Assign one owner and one reviewer per stream. These are **suggested assignments**, not five separate repositories.

| Stream | First deliverable | Later deliverables | Dependency |
| --- | --- | --- | --- |
| A — Identity | Register, verify, login, logout, reset using the V3 opaque bearer-session table; real Spring Security role/ownership checks | Profile, addresses, Admin account/role/settings/audit endpoints | Unblocks protected APIs |
| B — Catalog | Product detail, category list, Manager product/category CRUD | Voucher CRUD, reviews/moderation | Can start now |
| C — Shopping | Customer cart API and UI; guest-cart merge | Checkout, reservation, simulated QR/COD, order history/cancel | Requires A for customer ownership; B for product data |
| D — Fulfillment | Warehouse stock view/adjustment/packing | Assignment, delivery attempts, returns, refunds | Requires orders from C |
| E — Integration/UI | Shared frontend routes, forms, API error/loading handling | Tickets, daily reports, dashboards, integration tests | Coordinate with A–D contracts |

Members may start frontend screens with typed mock data while a backend endpoint is being built, but mocks must be replaced and an integration test added before the feature PR is merged. Do not claim a mock page is integrated.

## Definition of done for every feature PR

1. Claim/update the route and DTO in the API contract. Add its full OpenAPI schema before implementing it.
2. Add a new Flyway migration only if the approved schema needs to change; never rewrite applied migrations.
3. Enforce role **and ownership** in the backend. Admin does not inherit Manager/Warehouse rights.
4. Add success, validation, forbidden/ownership and state-transition tests as relevant.
5. Connect the frontend to the real API, including loading, empty and error states.
6. Run backend tests and frontend lint/build/tests. Open a feature-branch PR into `develop`, request one teammate review, wait for CI, then merge.

For QR, display "Simulated payment — no real money is transferred". Do not integrate a gateway or store real card/bank secrets.
