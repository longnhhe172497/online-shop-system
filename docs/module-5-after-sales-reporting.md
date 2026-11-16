# Module 5 — After-sales, Support and Reporting

Owner: Cao Sơn Tùng Dương (HE201082). SRS section 3.5; SDS sections II.4 and II.5.
Use cases: UC-CUS-014, UC-WH-002, UC-MNG-004, UC-CUS-019, UC-SUP-002, UC-MNG-006, UC-MNG-007, UC-MNG-010.

The module reuses the V2 tables `return_requests`, `return_request_items`, `return_inspections`, `refunds`, `support_tickets`, `ticket_messages`, `inventory_movements` and `audit_logs`, and reads `staff_daily_reports`. It does not change the V2 schema; `V15__after_sales_reporting_permissions.sql` only grants the role permissions listed at the end.

All paths below are relative to `http://localhost:8080`.

## Return and refund workflow (UC-CUS-014, UC-WH-002, UC-MNG-004)

| Step | Actor | Endpoint |
|---|---|---|
| Submit a request for a delivered order | Customer | `POST /api/orders/{orderId}/returns` `{requestType: REFUND_ONLY \| RETURN_AND_REFUND, reason, evidenceUrl?, items:[{orderItemId, quantity}]}` |
| Follow own requests (status, Manager note) | Customer | `GET /api/orders/returns` |
| List requests | Manager | `GET /api/manager/returns?status=&page=&size=` |
| Decide | Manager | `POST /api/manager/returns/{id}/decision` `{approved, note}` (note required to reject) |
| Queue of authorized returns | Warehouse | `GET /api/warehouse/returns` |
| Inspect every item | Warehouse | `POST /api/warehouse/returns/{id}/inspection` `{items:[{returnRequestItemId, receivedQuantity, restockQuantity, conditionNote}]}` |
| Pending refunds | Manager | `GET /api/manager/refunds?status=PENDING` |
| Record completion | Manager | `POST /api/manager/refunds/{id}/complete` |
| Paid QR orders returned after failed delivery without a refund | Manager | `GET /api/after-sales/failed-deliveries` |
| Create their refund | Manager | `POST /api/after-sales/failed-deliveries/{orderId}/refund` |

Status flow: `REQUESTED` → (`REFUND_ONLY`) `REFUND_PENDING` or `REJECTED`; (`RETURN_AND_REFUND`) `RETURN_AUTHORIZED` → `INSPECTED` → `REFUND_PENDING` or `REJECTED`; completion sets the refund `COMPLETED` and the request `REFUNDED`.

Rules:
- The order must be `DELIVERED` and inside `system_settings.return_window_days` (default 7) counted from delivery; otherwise MSG-030.
- Requested quantity is capped by the quantity not yet requested (MSG-048). The refund amount is the line total minus the item's share of the order discount; shipping is not refunded.
- Inspection: received ≤ requested, restock ≤ received, condition note required (MSG-050). An item is `ELIGIBLE` when received > 0. Restocked units go back to stock with a `RETURN_RESTOCK` inventory movement.
- After inspection the refund covers only the received quantity of `ELIGIBLE` items; with no eligible item the Manager can only reject.
- Refund reasons: `CUSTOMER_RETURN`, `CANCELLED_PAID_ORDER` (created by order cancellation), `FAILED_PREPAID_DELIVERY`. Method `SIMULATED_QR` or `MANUAL_COD`. Refunds are only created for paid orders, and one idempotency key per source prevents duplicates.
- On the order page the customer sees each request with its status: MSG-032 when approved, MSG-033 with the Manager's reason when rejected.
- Every decision, inspection, refund creation and completion is written to `audit_logs`.

## Support tickets (UC-CUS-019, UC-SUP-002)

| Actor | Endpoint |
|---|---|
| Customer: own tickets / create | `GET /api/support/tickets`, `POST /api/support/tickets` `{subject, orderId?, message}` |
| Customer or Support: add a message | `POST /api/support/tickets/{id}/messages` `{message}` |
| Support: queue, filtered by status | `GET /api/support/tickets/queue?status=OPEN\|IN_PROGRESS\|RESOLVED\|CLOSED` |
| Support: change status | `PATCH /api/support/tickets/{id}/status` `{status: IN_PROGRESS \| RESOLVED \| CLOSED}` |

The first staff reply on an `OPEN` ticket assigns it to that staff member and moves it to `IN_PROGRESS`. Customers read and reply only to their own tickets (an order must also be theirs). A `CLOSED` ticket accepts no messages or status changes. Status changes are audited.

## Dashboard, staff daily reports and export (UC-MNG-006, UC-MNG-007, UC-MNG-010)

| Endpoint | Returns |
|---|---|
| `GET /api/manager/reports/dashboard?from=&to=` | summary tiles, daily series, top products, low stock, pending actions (default last 30 days) |
| `GET /api/manager/reports/summary?from=&to=` | daily rows and totals for the transaction report |
| `GET /api/manager/reports/export.csv?from=&to=` | CSV, one row per day and a total row |
| `GET /api/manager/reports/daily-reports?date=` | Warehouse and Delivery daily reports for the date (default today); the screen can filter them by staff member |

- Range: at most 366 days, start ≤ end, end not in the future; time zone `Asia/Ho_Chi_Minh`.
- Orders are counted by creation date. Collected revenue = paid QR payments by payment time plus collected COD by collection time. Refunds count only `COMPLETED` refunds by completion time. Net revenue = collected revenue − completed refunds.
- An empty range shows a warning; the CSV can still be exported with zero rows.
- Staff submit their daily reports in the logistics module (`/api/internal/{warehouse|delivery}/reports/daily`); this module only reads them.

## Payment limitation

QR payment is simulated and COD is recorded manually. Completing a refund records the Manager's confirmation; it does **not** call a payment gateway or move real money. The UI says so.

## Permissions (V15)

New: `MANAGER_REFUND_COMPLETE`, `MANAGER_REPORT_VIEW`, `SUPPORT_TICKET_VIEW`, `SUPPORT_TICKET_REPLY`. Reused: `MANAGER_RETURN_DECIDE`, `WAREHOUSE_RETURN_INSPECT`.

## Tests

- Unit (JUnit + Mockito): `AfterSalesServiceUnitTests` (29), `SupportTicketUnitTests` (10), `ReportingServiceUnitTests` (10).
- Integration (database): `AfterSalesIntegrationTests` (9), `ReportingIntegrationTests` (5), including ownership and 403 checks.
- Frontend (Vitest): `ManagerAfterSalesPage.test.tsx`, `AfterSalesCustomerSupport.test.tsx`.
