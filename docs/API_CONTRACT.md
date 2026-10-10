# Online Shop System API contract — route and DTO baseline

This is the **agreed route inventory for implementation**, not a claim that every route exists. The machine-readable [`openapi.yaml`](../openapi.yaml) contains implemented routes only. A feature owner must add complete OpenAPI request/response schemas and examples in the same PR as the endpoint. Frontend and backend must review that contract together before coding the feature.

## Shared rules

- Base URL `http://localhost:8080/api`; JSON bodies; UTF-8; timestamps ISO 8601 with UTC offsets; reports displayed in `Asia/Ho_Chi_Minh`; all amounts VND.
- Pagination: `page` zero-based, `size` default 20 and range 1–100. List response `{items, page, size, totalItems, totalPages}`.
- Error: correct HTTP status, `application/problem+json` with `type`, `title`, `status`, `detail`, and stable application `code`. No 200-with-error-body; no stack traces or secrets.
- DTOs are separate from persistence entities. Order items carry name/SKU/price snapshots. Any resource with a customer ID must be checked for ownership server-side.
- `401` means not logged in; `403` means logged in without permission; `404` means absent or not visible to the caller; `409` means state conflict (stock, duplicate review, illegal transition); `422` means a business rule failed after syntactically valid input.
- Admin is a separate role, not a superclass of Manager, Support, Warehouse or Delivery.
- Authentication supports `Authorization: Bearer <opaque-token>` for API clients and an HttpOnly `FORME_SESSION` cookie for the browser. Only the SHA-256 token hash is stored in `user_sessions`; sessions have a 24-hour absolute lifetime and expire after 30 minutes without an authenticated request (server-enforced). There is no refresh token. Browser writes require the `X-XSRF-TOKEN` header obtained from `GET /auth/csrf`. Sessions are rechecked against user status on every request. MFA-enabled accounts complete an email-code or TOTP challenge, selected by `mfaMethod`; a previously issued, unused recovery code may replace the factor at login. The browser warns after about 25 idle minutes, but the server decides whether a session is valid.
- QR and refunds are local simulations. They must say so in API and UI; never imply real money has moved.

## Endpoint ownership and minimum request/response fields

`P` = public, `C` = customer-owned resource, `M` = Manager, `S` = Support, `W` = Warehouse, `D` = Delivery, `A` = Admin. All mutating endpoints require an authenticated actor except registration, verification, login and password-reset routes. The feature owner must define precise validation and example values in OpenAPI.

| Method and path | Role | Minimum request → response |
| --- | --- | --- |
| `GET /health` | P | none → `{status, service, timestamp}` |
| `GET /products` | P | `page,size` → page of `{id,sku,name,description,imageUrl,price,categoryName,availableQuantity}` **implemented** |
| `GET /products/{id}` | P | none → product detail |
| `GET /categories` | P | none → active categories |
| `POST /auth/register` | P | `{email,password,confirmPassword,fullName,phone}` → `{userId,verificationRequired,emailSent}`; passwords must match |
| `POST /auth/verify` | P | `{token}` → `{verified}` |
| `POST /auth/login` | P | `{email,password}` → `{accessToken,expiresAt,user:{id,fullName,role}}`; opaque 24-hour bearer token |
| `GET /auth/csrf` | P | obtain the browser's XSRF token cookie before a write |
| `POST /auth/browser-login` | P | `{email,password}` → HttpOnly session cookie and user, or MFA challenge; no bearer token in the browser response |
| `POST /auth/mfa/verify`, `POST /auth/browser-mfa/verify` | P | `{challengeToken,code}` where `code` is a six-digit email/TOTP code or a single-use recovery code → bearer session or browser cookie, respectively |
| `POST /auth/verification/resend` | P | `{email}` → `202`; response does not disclose whether a pending account exists |
| `POST /auth/logout` | C/M/S/W/D/A | authenticated request → `204` |
| `POST /auth/password-reset/request` | P | `{email}` → `202` regardless of whether account exists; active accounts receive a 30-minute single-use link, at most one email per five minutes |
| `POST /auth/password-reset/confirm` | P | `{token,newPassword,confirmPassword}` → `{reset:true}`; consumes link and revokes existing sessions |
| `GET /me`, `PATCH /me` | authenticated | read profile / `{fullName,phone}` → profile without password hash; email and role stay read-only |
| `GET /me/limits`, `GET /me/security` | authenticated | saved-address limit / `{mfaEnabled,mfaMethod,recoveryCodesRemaining}`; raw recovery codes are never returned again |
| `POST /me/password` | authenticated | `{currentPassword,newPassword,confirmPassword}` → `204`; revokes all sessions |
| `GET /me/sessions`, `DELETE /me/sessions/{id}`, `POST /me/sessions/revoke-other` | authenticated | list/revoke owned sessions |
| `POST /me/mfa/start`, `POST /me/mfa/confirm` | authenticated | email-code enrollment; confirm returns `{recoveryCodes:[...]}` once |
| `POST /me/mfa/totp/start`, `POST /me/mfa/totp/confirm` | authenticated | `{password}` → `{secret,otpauthUri}` (one-time setup display), then `{code}` → `{recoveryCodes:[...]}`. TOTP uses six digits every 30 seconds; the active secret is encrypted locally. Only one MFA method may be active. |
| `POST /me/mfa/step-up/start` | authenticated | `{password}` → `{challengeToken,mfaMethod}`; email MFA sends a code, TOTP uses the authenticator app. Required before disabling MFA or regenerating recovery codes. |
| `POST /me/mfa/disable` | authenticated | `{password,challengeToken,code}` → `204`; current MFA factor or unused recovery code required; all remaining codes invalidated and user notified by email |
| `POST /me/mfa/recovery/regenerate` | authenticated | `{password,challengeToken,code}` → `{recoveryCodes:[...]}`; current factor or unused recovery code required, all old codes invalidated and user notified |
| `POST /me/email-change`, `POST /auth/email-change/confirm` | authenticated / P | current password + new email sends a 30-minute link; confirmation changes login email and revokes sessions |
| `GET/POST /me/addresses` | C | none / `{recipientName,phone,addressLine,ward,district,province,isDefault}` → own addresses / created address; first address becomes default, max from `max_saved_addresses` setting |
| `PATCH/DELETE /me/addresses/{id}` | C | complete address fields / none → updated address / `204`; owner check uses bearer user ID, deleting default promotes oldest remaining address |
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
| `GET /admin/users`, `GET/PATCH /admin/users/{id}`, `PATCH /admin/users/{id}/role`, `PATCH /admin/users/{id}/status`, `POST /admin/users/{id}/sessions/revoke` | A | search/update managed accounts; detail includes active sessions and recent history; Admin may revoke sessions. Protect final active Admin. Staff accounts are created only through invitations. |
| `GET/POST /admin/invitations`, `POST /admin/invitations/{id}/resend`, `POST /admin/invitations/{id}/revoke`, `POST /invitations/accept` | A / P | Admin emails a staff invite; recipient sets their own password using the single-use link. List/creation response includes `status`: `PENDING`, `STALLED`, `SENT`, `FAILED`, `REVOKED`, `EXPIRED`, or `ACCEPTED`. A `PENDING` send older than five minutes is displayed as `STALLED`; Admin may replace failed, expired or stalled invitations with a new link. The old link is invalidated. An unaccepted invitation may be revoked. |
| `GET /admin/audit-logs`, `GET/PATCH /admin/settings` | A | audit supports `actor`, `action`, `from`, `to` filters and paging; login success/failure is logged without secrets. Settings remain validated. |

## State rules that all modules share

- QR order: `AWAITING_PAYMENT → CONFIRMED → READY_FOR_DELIVERY → ASSIGNED → DELIVERING → DELIVERED`; a failed/expired *attempt* does not change the order to a separate payment-failed status. COD begins `PENDING_CONFIRMATION`, then Support confirms it.
- QR expiry releases stock/voucher reservation. Retry performs fresh price, stock, product and voucher validation and creates a new reservation and payment attempt. At most three QR attempts.
- Cancellation is allowed before packing; paid QR cancellations create a simulated refund record. Packing consumes reservation once and reduces on-hand once.
- A shipment has at most two delivery attempts. COD becomes paid only when the assigned Delivery Staff confirms successful delivery and collection.
- Return requests must concern owned delivered order items within seven days; returned quantity across requests cannot exceed purchased quantity. Physical returns require inspection before approval.

## Feature PR checklist

Define OpenAPI schema and examples → implement backend validation/role/ownership/transaction → add backend tests → connect frontend to the real API → add frontend tests → CI and review. Never edit applied Flyway V2; add V3+ migrations.
