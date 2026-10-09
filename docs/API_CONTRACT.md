# Online Shop System API contract — route and DTO baseline

This is the **agreed route inventory for implementation**, not a claim that every route exists. The machine-readable [`openapi.yaml`](../openapi.yaml) contains implemented routes only. A feature owner must add complete OpenAPI request/response schemas and examples in the same PR as the endpoint. Frontend and backend must review that contract together before coding the feature.

## Shared rules

- Base URL `http://localhost:8080/api`; JSON bodies; UTF-8; timestamps ISO 8601 with UTC offsets; reports displayed in `Asia/Ho_Chi_Minh`; all amounts VND.
- Pagination: `page` zero-based, `size` default 20 and range 1–100. List response `{items, page, size, totalItems, totalPages}`.
- Error: correct HTTP status, `application/problem+json` with `type`, `title`, `status`, `detail`, and stable application `code`. No 200-with-error-body; no stack traces or secrets.
- DTOs are separate from persistence entities. Order items carry name/SKU/price snapshots. Any resource with a customer ID must be checked for ownership server-side.
- `401` means not logged in; `403` means logged in without permission; `404` means absent or not visible to the caller; `409` means state conflict (stock, duplicate review, illegal transition); `422` means a business rule failed after syntactically valid input.
- Admin is a separate role, not a superclass of Manager, Support, Warehouse or Delivery.
- Authentication baseline: `Authorization: Bearer <opaque-token>`. Login creates a random token; only its SHA-256 hash is stored in `user_sessions`, with a 24-hour expiry. Logout revokes that session. There is no refresh token in v1. Registration, verification, login and password-reset routes are public; all other authenticated routes verify the session and user status on every request.
- QR and refunds are local simulations. They must say so in API and UI; never imply real money has moved.

## Endpoint ownership and minimum request/response fields

`P` = public, `C` = customer-owned resource, `M` = Manager, `S` = Support, `W` = Warehouse, `D` = Delivery, `A` = Admin. All mutating endpoints require an authenticated actor except registration, verification, login and password-reset routes. The feature owner must define precise validation and example values in OpenAPI.

| Method and path | Role | Minimum request → response |
| --- | --- | --- |
| `GET /health` | P | none → `{status, service, timestamp}` |
| `GET /products` | P | `page,size` → page of `{id,sku,name,description,imageUrl,price,categoryName,availableQuantity}` **implemented** |
| `GET /products/{id}` | P | none → product detail |
| `GET /categories` | P | none → active categories |
| `POST /auth/register` | P | `{email,password,confirmPassword,fullName,phone}` → `{userId,verificationRequired}`; passwords must match |
| `POST /auth/verify` | P | `{token}` → `{verified}` |
| `POST /auth/login` | P | `{email,password}` → `{accessToken,expiresAt,user:{id,fullName,role}}`; opaque 24-hour bearer token |
| `POST /auth/logout` | C/M/S/W/D/A | authenticated request → `204` |
| `POST /auth/password-reset/request` | P | `{email}` → `202` regardless of whether account exists; active accounts receive a 30-minute single-use link, at most one email per five minutes |
| `POST /auth/password-reset/confirm` | P | `{token,newPassword,confirmPassword}` → `{reset:true}`; consumes link and revokes existing sessions |
| `GET /me`, `PATCH /me` | authenticated | profile read/update → profile without password hash |
| `GET/POST /me/addresses` | C | none / address fields → own addresses / created address |
| `PATCH/DELETE /me/addresses/{id}` | C | address fields / none → updated address / `204` |
| `GET /cart` | C or guest session | none → cart items and current totals |
| `POST /cart/items` | C or guest session | `{productId,quantity}` → cart; revalidate product and quantity |
| `PATCH/DELETE /cart/items/{id}` | C or guest session | `{quantity}` / none → cart / `204` |
| `POST /cart/voucher` | C | `{code}` → recalculated cart discount; final eligibility checked again at checkout |
| `POST /orders` | C | `{addressId,paymentMethod,voucherCode?}` → order snapshot, status and total |
| `GET /orders`, `GET /orders/{id}` | C | paging / ID → own order summaries/detail |
| `POST /orders/{id}/cancel` | C | `{reason?}` → order status and refund-pending information if already paid |
| `POST /orders/{id}/payment-attempts` | C | none → simulated QR attempt `{id,code,expiresAt,status}` after stock/voucher revalidation |
| `POST /payment-attempts/{id}/simulate` | C (own) | `{outcome:SUCCESS\|FAILURE}` → new attempt/payment and order status; development simulation only |
| `GET /orders/{id}/shipment` | C | none → own delivery status/history |
| `GET /support/orders/pending`, `POST /support/orders/{id}/confirm` | S | none / `{note?}` → COD queue / confirmed order |
| `GET /warehouse/packing-queue`, `POST /warehouse/orders/{id}/pack` | W | none / `{note?}` → queue / order ready for delivery |
| `GET /warehouse/inventory` | W | paging/filter → `{productId,sku,stockOnHand,stockReserved,availableQuantity}` page |
| `POST /warehouse/inventory/adjustments` | W | `{productId,newQuantity,reason}` → new balance and immutable movement |
| `GET /manager/orders`, `POST /manager/orders/{id}/assign` | M | filters / `{deliveryStaffId}` → order page / shipment assignment |
| `GET /delivery/assignments`, `PATCH /delivery/attempts/{id}` | D | none / `{status,failureReason?,codCollected?}` → own assignments / updated attempt |
| `POST /orders/{id}/returns`, `GET /returns/{id}` | C | `{type,reason,items:[{orderItemId,quantity}],evidenceUrl?}` / none → request / own request |
| `POST /warehouse/returns/{id}/inspection` | W | item inspection, condition and restock quantity → inspection result |
| `POST /manager/returns/{id}/decision` | M | `{decision,approvedAmount?,reason}` → approved/rejected status; refund simulated/manual |
| `POST /support/tickets`, `GET /support/tickets` | C | `{subject,orderId?,message}` / paging → ticket / own tickets |
| `GET /support/tickets/{id}`, `POST /support/tickets/{id}/messages` | C (own) or S | none / `{message}` → conversation / new message |
| `POST /order-items/{id}/review` | C (own delivered item) | `{rating,comment?}` → review |
| `GET /products/{id}/reviews` | P | paging → visible reviews |
| `PATCH /manager/reviews/{id}` | M | `{status}` → moderated review |
| `GET/POST/PATCH/DELETE /manager/products`, `/manager/categories`, `/manager/vouchers` | M | feature DTO → catalog/promotion resource; exact ID routes in OpenAPI |
| `GET /manager/dashboard`, `GET /manager/reports` | M | date/filter → aggregate metrics/exportable data |
| `POST /staff/daily-reports` | S/W/D | `{reportDate,content}` → report |
| `GET /manager/daily-reports` | M | paging/date/staff → report page |
| `GET/POST/PATCH /admin/users`, `PATCH /admin/users/{id}/role`, `PATCH /admin/users/{id}/status` | A | user/filter/role/status DTO → managed account; protect final active Admin |
| `GET /admin/audit-logs`, `GET/PATCH /admin/settings` | A | paging / setting key/value → immutable audit page / validated settings |

## State rules that all modules share

- QR order: `AWAITING_PAYMENT → CONFIRMED → READY_FOR_DELIVERY → ASSIGNED → DELIVERING → DELIVERED`; a failed/expired *attempt* does not change the order to a separate payment-failed status. COD begins `PENDING_CONFIRMATION`, then Support confirms it.
- QR expiry releases stock/voucher reservation. Retry performs fresh price, stock, product and voucher validation and creates a new reservation and payment attempt. At most three QR attempts.
- Cancellation is allowed before packing; paid QR cancellations create a simulated refund record. Packing consumes reservation once and reduces on-hand once.
- A shipment has at most two delivery attempts. COD becomes paid only when the assigned Delivery Staff confirms successful delivery and collection.
- Return requests must concern owned delivered order items within seven days; returned quantity across requests cannot exceed purchased quantity. Physical returns require inspection before approval.

## Feature PR checklist

Define OpenAPI schema and examples → implement backend validation/role/ownership/transaction → add backend tests → connect frontend to the real API → add frontend tests → CI and review. Never edit applied Flyway V2; add V3+ migrations.
