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

## Five balanced workstreams

Assign **one owner and one different reviewer** to each stream. The point estimates balance complexity rather than count use cases; revisit them after the first milestone. Every owner delivers backend API, frontend screens, OpenAPI updates and tests for their stream. No stream is “frontend only” or “backend only”. The five streams are suggested assignments, not five separate repositories.

| Stream | Scope and deliverable | Estimate | Dependency / earliest start |
| --- | --- | ---: | --- |
| **A — Identity & Admin** | Register, email verification, login/logout with V3 bearer sessions, password reset; profile and saved addresses; role and ownership enforcement; Admin accounts, role/status, settings and audit. Do not give Admin other business-role privileges. | **25** | Start now. Deliver login and role/ownership checks first to unblock protected APIs. |
| **B — Catalog & Cart** | Public product list/detail/search and categories; Manager product/category CRUD; Guest and Customer cart, cart merge on login; verified product reviews and Manager moderation. | **24** | Public catalog can start now. Customer cart integration waits for A's login contract. |
| **C — Checkout & Payment** | Voucher CRUD/application; checkout and stock/voucher reservations; simulated QR and COD; QR retry/expiry; customer order history/detail/cancel; order receipt email. Never imply that simulated payment moves real money. | **26** | Contract and tests can start now; integration depends on A and B. |
| **D — Warehouse & Delivery** | Basic stock view/manual adjustment, Support confirmation of COD orders, packing and stock-out; Manager delivery assignment; Delivery Staff attempts, COD collection, failed delivery and return to warehouse. Maximum two attempts. | **25** | Inventory UI can start now; fulfillment integration depends on C's orders. |
| **E — After-sales & Reporting** | Customer return/refund request, Warehouse inspection, Manager decision, simulated/manual refund; support tickets/messages; staff daily reports; Manager dashboard and business report/export. | **25** | Ticket contract/UI can start now; refunds and metrics depend on C/D transaction data. |

Do not assign a second owner to the same endpoint. The owner of a stream owns its related Flyway additions, API routes and UI; another member reviews the PR. For cross-stream changes, agree on the request/response schema in [`API_CONTRACT.md`](API_CONTRACT.md) and OpenAPI first, then split implementation at that boundary.

## Suggested integration milestones

1. **Foundation:** A completes login/authorization; B completes public catalog. The whole team can query products and access one protected endpoint with a real account.
2. **Purchase:** B completes cart; C completes checkout and simulated payment. A customer can place and view an order.
3. **Fulfillment:** D packs and delivers that order; COD is marked paid only after collection.
4. **After-sales:** E handles return/refund/ticket and shows reports from committed records.

Members may build screens against typed mock data while a backend endpoint is being developed, but mocks must be replaced and an integration test added before the feature PR is merged. Do not claim a mock page is integrated.

## Definition of done for every feature PR

1. Claim/update the route and DTO in the API contract. Add its full OpenAPI schema before implementing it.
2. Add a new Flyway migration only if the approved schema needs to change; never rewrite applied migrations.
3. Enforce role **and ownership** in the backend. Admin does not inherit Manager/Warehouse rights.
4. Add success, validation, forbidden/ownership and state-transition tests as relevant.
5. Connect the frontend to the real API, including loading, empty and error states.
6. Run backend tests and frontend lint/build/tests. Open a feature-branch PR into `develop`, request one teammate review, wait for CI, then merge.

For QR, display “Simulated payment — no real money is transferred”. Do not integrate a gateway or store real card/bank secrets.
