# Online Shop System API conventions

This file is the **planned endpoint inventory**, not a claim that every route is implemented. The source-of-truth OpenAPI description for currently implemented routes is [`openapi.yaml`](../openapi.yaml). Add the full request, response, errors and role rules to OpenAPI before implementing a planned route.

## Shared rules

- Base URL: `http://localhost:8080/api`; JSON request/response; timestamps in ISO 8601 UTC, displayed in `Asia/Ho_Chi_Minh` for business reports; money in VND.
- Collection pagination: `page` is zero-based, `size` defaults to 20 and is limited to 1–100. Response: `{items, page, size, totalItems, totalPages}`.
- Validation errors: HTTP 400 with `application/problem+json` and a stable `code`; authorization failures use 401/403. Do not return `200` for errors.
- Do not expose database entities or password hashes in API responses. Use request/response DTOs.
- Mutating endpoints require the role listed below; the backend must also verify ownership of customer resources. Admin does **not** inherit Manager or Warehouse permissions.
- QR and refund operations are local simulations, never real payments.

## Planned route groups

| Area | Routes | Access |
| --- | --- | --- |
| Catalog | `GET /products`, `GET /products/{id}`, `GET /categories` | Public |
| Accounts | `POST /auth/register`, `POST /auth/verify`, `POST /auth/login`, `POST /auth/logout`, `POST /auth/password-reset`; `GET/PATCH /me`, `GET/POST/PATCH/DELETE /me/addresses` | Guest/own account |
| Cart | `GET /cart`, `POST /cart/items`, `PATCH/DELETE /cart/items/{id}`, `POST /cart/voucher` | Customer; guest cart limited to browsing/session |
| Checkout | `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel` | Customer, own orders |
| QR simulation | `POST /orders/{id}/payment-attempts`, `POST /payment-attempts/{id}/simulate` | Customer, own order; local-only simulation |
| Warehouse | `GET /warehouse/packing-queue`, `POST /warehouse/orders/{id}/pack`, `GET /warehouse/inventory`, `POST /warehouse/inventory/adjustments` | Warehouse |
| Delivery | `GET /delivery/assignments`, `POST /delivery/shipments/{id}/attempts`, `PATCH /delivery/attempts/{id}` | Delivery, assigned shipments |
| Support | `GET/PATCH /support/orders/{id}`, `GET/PATCH /support/tickets/{id}`, `POST /support/tickets/{id}/messages` | Support |
| Returns | `POST /orders/{id}/returns`, `GET /returns/{id}`, `POST /warehouse/returns/{id}/inspection`, `POST /manager/returns/{id}/decision` | Customer own, Warehouse, Manager respectively |
| Feedback | `POST /order-items/{id}/review`, `GET /products/{id}/reviews`, `PATCH /manager/reviews/{id}` | Customer own delivered item, Public, Manager respectively |
| Manager | `GET /manager/orders`, `POST /manager/orders/{id}/assign`, `GET /manager/reports`, `GET /manager/dashboard`, catalog/voucher CRUD under `/manager` | Manager |
| Admin | `GET/POST/PATCH /admin/users`, `PATCH /admin/users/{id}/role`, `PATCH /admin/users/{id}/status`, `GET /admin/audit-logs`, `GET/PATCH /admin/settings` | Admin only |
| Staff reports | `POST /staff/daily-reports`, `GET /manager/daily-reports` | Staff own, Manager |

Route names in this table are a starting agreement. Before two teammates implement the same feature on opposite sides, its OpenAPI request/response schema and acceptance examples must be reviewed in one PR.
