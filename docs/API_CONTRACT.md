# Online Shop System API contract — route and DTO baseline

This is the **agreed route inventory for implementation**, not a claim that every route exists. The machine-readable [`openapi.yaml`](../openapi.yaml) contains implemented routes only. A feature owner must add complete OpenAPI request/response schemas and examples in the same PR as the endpoint. Frontend and backend must review that contract together before coding the feature.

## Shared rules

- Base URL `http://localhost:8080/api`; JSON bodies; UTF-8; timestamps ISO 8601 with UTC offsets; reports displayed in `Asia/Ho_Chi_Minh`; all amounts VND.
- Pagination: `page` zero-based, `size` default 20 and range 1–100. List response `{items, page, size, totalItems, totalPages}`.
- Error: correct HTTP status, `application/problem+json` with `type`, `title`, `status`, `detail`, and stable application `code`. No 200-with-error-body; no stack traces or secrets.
- DTOs are separate from persistence entities. Order items carry name/SKU/price snapshots. Any resource with a customer ID must be checked for ownership server-side.
- `401` means not logged in; `403` means logged in without permission; `404` means absent or not visible to the caller; `409` means state conflict (stock, duplicate review, illegal transition); `422` means a business rule failed after syntactically valid input.
- Admin is a separate role, not a superclass of Manager, Support, Warehouse or Delivery.
- Admin may configure grants for Manager, Support, Warehouse and Delivery through `GET /admin/role-permissions` and `PUT /admin/role-permissions/{role}` with a version number. Admin/Customer grants are fixed. Only catalogued permission codes are accepted. Route-level checks read grants on every request, so active staff sessions see changes immediately; domain ownership and business-state checks remain mandatory. Unmapped business routes are denied until explicitly registered in the backend permission policy. V10 seeds the previous role boundaries. The UI labels planned business endpoints honestly: granting access does not implement those endpoints.
- Authentication supports `Authorization: Bearer <opaque-token>` for API clients and an HttpOnly `FORME_SESSION` cookie for the browser. Only the SHA-256 token hash is stored in `user_sessions`; sessions have a 24-hour absolute lifetime and expire after 30 minutes without an authenticated request (server-enforced). There is no refresh token. Browser writes require the `X-XSRF-TOKEN` header obtained from `GET /auth/csrf`. Sessions are rechecked against user status on every request. MFA-enabled accounts complete an email-code or TOTP challenge, selected by `mfaMethod`; a previously issued, unused recovery code may replace the factor at login. The browser warns after about 25 idle minutes, but the server decides whether a session is valid.
- Optional Google Identity Services sign-in exchanges a signed Google ID token for the existing browser session/MFA flow. New users are `CUSTOMER` only. Existing emails require explicit linking after password login; Google email alone never grants an existing account or staff role. `GOOGLE_CLIENT_ID` is a public OAuth Web client ID, not a client secret.
- QR and refunds are local simulations. They must say so in API and UI; never imply real money has moved.
- Guest carts use an HttpOnly `FORME_CART` cookie holding a random token; only its SHA-256 hash is stored. On login the guest cart merges into the customer cart (quantities summed, capped at available stock) and the guest cart expires. Carts never reserve stock; checkout revalidates price and stock. Categories are a flat list and product "combos" are out of scope; promotions are vouchers only (Stream C).
## Endpoint ownership and minimum request/response fields

`P` = public, `C` = customer-owned resource, `M` = Manager, `S` = Support, `W` = Warehouse, `D` = Delivery, `A` = Admin. All mutating endpoints require an authenticated actor except registration, verification, login and password-reset routes. The feature owner must define precise validation and example values in OpenAPI.

| Method and path | Role | Minimum request → response |
| --- | --- | --- |
| `GET /health` | P | none → `{status, service, timestamp}` |
| `GET /products` | P | `page,size,q,categoryId,minPrice,maxPrice,sort` (`newest`, `price_asc`, `price_desc`, `name`) → page of products; only ACTIVE products in ACTIVE categories. `q` matches name, description and category name. |
| `GET /products/{id}` | P | none → `{...product, avgRating, reviewCount}`; `404` if the product or its category is not ACTIVE |
| `GET /categories` | P | none → ACTIVE categories `{id,name,description}` (flat list, no parent) |
| `POST /auth/register` | P | `{email,password,confirmPassword,fullName,phone}` → `{userId,verificationRequired,emailSent}`; passwords must match |
| `POST /auth/verify` | P | `{token}` → `{verified}` |
| `POST /auth/login` | P | `{email,password}` → `{accessToken,expiresAt,user:{id,fullName,role}}`; opaque 24-hour bearer token |
| `GET /auth/csrf` | P | obtain the browser's XSRF token cookie before a write |
| `POST /auth/browser-login` | P | `{email,password}` → HttpOnly session cookie and user, or MFA challenge; no bearer token in the browser response |
| `GET /auth/google/config`, `POST /auth/browser-google-login` | P | `{enabled,clientId}` / `{credential}` → browser cookie and user, or MFA challenge; existing email without link → `409` |
| `POST /auth/mfa/verify`, `POST /auth/browser-mfa/verify` | P | `{challengeToken,code}` where `code` is a six-digit email/TOTP code or a single-use recovery code → bearer session or browser cookie, respectively |
| `POST /auth/verification/resend` | P | `{email}` → `202`; response does not disclose whether a pending account exists |
| `POST /auth/logout` | C/M/S/W/D/A | authenticated request → `204` |
| `POST /auth/password-reset/request` | P | `{email}` → `202` regardless of whether account exists; active accounts receive a 30-minute single-use link, at most one email per five minutes |
| `POST /auth/password-reset/confirm` | P | `{token,newPassword,confirmPassword}` → `{reset:true}`; consumes link and revokes existing sessions |
| `GET /me`, `PATCH /me` | authenticated | read profile / `{fullName,phone}` → profile without password hash; email and role stay read-only |
| `GET /me/limits`, `GET /me/security` | authenticated | saved-address limit / `{mfaEnabled,mfaMethod,recoveryCodesRemaining}`; raw recovery codes are never returned again |
| `GET /me/google`, `POST /me/google/link` | authenticated | `{linked}` / `{password,credential}` → `204`; password and matching verified Google email required |
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
| `GET /cart` | C or guest | none → `{items:[{id,productId,sku,name,imageUrl,unitPrice,quantity,availableQuantity,lineTotal,warning?}],itemCount,subtotal}`; `warning` is `PRODUCT_UNAVAILABLE` or `INSUFFICIENT_STOCK` (same codes as checkout) and flagged lines are excluded from `subtotal`; staff accounts get `403` |
| `POST /cart/items` | C or guest | `{productId,quantity 1-99}` → cart; adds to an existing line; first guest add sets the `FORME_CART` cookie; `404` unknown product, `409` `PRODUCT_UNAVAILABLE` / `INSUFFICIENT_STOCK` / `QUANTITY_LIMIT` |
| `PATCH/DELETE /cart/items/{id}` | C or guest | `{quantity 1-99}` / none → cart; raising a quantity is stock-checked, lowering always works; another cart's item ID returns `404` |
| `POST /orders/preview` (replaces `POST /cart/voucher`) | C | `{voucherCode?}` → read-only quote of the `ACTIVE` cart: lines with `problem` (`PRODUCT_UNAVAILABLE`/`INSUFFICIENT_STOCK`), subtotal, discount, shipping, total, `canPlaceOrder` **implemented**; voucher errors as for `POST /orders`; final eligibility is checked again at checkout |
| `POST /orders` | C | `{addressId,paymentMethod,voucherCode?}` → `201` order detail **implemented**. Uses the caller's `ACTIVE` cart, locks product rows, re-checks product/category status and available stock, reserves stock (`stock_reserved`, never `stock_on_hand`), reserves the voucher and marks the cart `CONVERTED`. `SIMULATED_QR` → `AWAITING_PAYMENT` with a `PENDING` attempt expiring after `qr_expiry_minutes`; `COD` → `PENDING_CONFIRMATION` with an `UNPAID` attempt. Flat `shipping_fee` system setting. Errors: `422 CART_EMPTY`, `404 ADDRESS_NOT_FOUND`, `409 PRODUCT_UNAVAILABLE`, `409 INSUFFICIENT_STOCK`, `422 VOUCHER_INVALID`, `422 VOUCHER_NOT_ACTIVE`, `422 VOUCHER_MIN_ORDER_NOT_MET`, `409 VOUCHER_USAGE_LIMIT_REACHED` |
| `GET /orders`, `GET /orders/{id}` | C | paging / ID → own order summaries (newest first) / detail with items and latest payment attempt **implemented**; another customer's order → `404 ORDER_NOT_FOUND` |
| `POST /orders/{id}/cancel` | C | `{reason?}` → order detail **implemented**. Allowed in `AWAITING_PAYMENT`, `PENDING_CONFIRMATION`, `CONFIRMED`; releases stock and voucher, cancels unfinished attempts; a paid order gets a `PENDING` simulated refund (`CANCELLED_PAID_ORDER`). Otherwise `409 ORDER_NOT_CANCELLABLE` |
| `POST /orders/{id}/payment-attempts` | C | none → `201` order detail with the new `PENDING` attempt **implemented**. Re-checks product status, unchanged price (`409 PRICE_CHANGED`), stock and voucher; at most `max_qr_attempts`. Errors: `409 PAYMENT_RETRY_NOT_ALLOWED`, `PAYMENT_ATTEMPT_ALREADY_PENDING`, `PAYMENT_ATTEMPTS_EXHAUSTED` |
| `POST /payment-attempts/{id}/simulate` | C (own) | `{outcome:SUCCESS\|FAILURE}` → order detail **implemented**; development simulation only. SUCCESS: attempt `PAID`, order `CONFIRMED`, voucher `CONSUMED`. FAILURE: attempt `FAILED`, stock and voucher released; after the last allowed attempt the order is `CANCELLED`. Overdue → `409 PAYMENT_ATTEMPT_EXPIRED`. A background job expires overdue attempts every 30 s |
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
| `POST /order-items/{id}/review`, `PATCH /order-items/{id}/review` | C (own delivered item) | `{rating 1-5,comment?}` → review; `409` if one already exists (use PATCH to edit) |
| `GET /products/{id}/reviews` | P | `page,size` → VISIBLE reviews only |
| `GET /manager/reviews`, `PATCH /manager/reviews/{id}` | M (`MANAGER_REVIEW_MODERATE`) | `status` filter / `{status: VISIBLE\|HIDDEN}` |
| `GET/POST /manager/products`, `GET /manager/products/category-options`, `GET/PATCH /manager/products/{id}`, `PATCH /manager/products/{id}/status` | M (`MANAGER_PRODUCT_MANAGE`) | `{sku,name,description?,imageUrl?,price,categoryId}` → product with `status`, read-only `stockOnHand`/`stockReserved`/`availableQuantity` **implemented**. New products start `DRAFT` with stock 0; stock is never writable here (Warehouse owns it). SKUs are stored upper-case and unique (`409 SKU_ALREADY_EXISTS`); missing or archived category `422 CATEGORY_INVALID`; publishing (`ACTIVE`) needs an ACTIVE category (`409 CATEGORY_NOT_ACTIVE`). No hard delete: use `ARCHIVED`. Every change is audited (`PRODUCT_*`). |
| `GET/POST /manager/categories`, `PATCH/DELETE /manager/categories/{id}`, `PATCH /manager/categories/{id}/status` | M (`MANAGER_CATEGORY_MANAGE`) | `{name,description?}` → category with `status`, `productCount`, `activeProductCount` **implemented**. Flat list; names unique ignoring case (`409 CATEGORY_NAME_EXISTS`). `INACTIVE` hides the category and its products; `ARCHIVED` and `DELETE` are refused while it still holds (non-archived / any) products (`409 CATEGORY_HAS_PRODUCTS`). Every change is audited (`CATEGORY_*`). |
| `GET/POST/PATCH/DELETE /manager/vouchers` | M | feature DTO → promotion resource (Stream C); exact ID routes in OpenAPI |
| `GET/POST /manager/vouchers`, `PATCH/DELETE /manager/vouchers/{id}` | M (`MANAGER_VOUCHER_MANAGE`) | `{code,discountType,discountValue,maxDiscount?,minOrderAmount,usageLimit,validFrom,validUntil,status}` → voucher with `usedCount` **implemented**. Codes are stored upper-case and unique (`409 VOUCHER_CODE_EXISTS`); a used voucher cannot be deleted (`409 VOUCHER_IN_USE`) and its `usageLimit` cannot drop below usage (`409 VOUCHER_LIMIT_BELOW_USAGE`) |
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
