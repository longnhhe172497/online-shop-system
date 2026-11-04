# Module 5 — Support, Refund Management & Reporting

This module reuses the existing `return_requests`, `return_request_items`, `return_inspections`, `refunds`, `support_tickets`, `ticket_messages`, `inventory_movements`, and `audit_logs` tables. It does not rewrite the existing V2 schema; V14 only grants the additional role permissions.

## Return and refund workflow

1. A customer submits a return/refund request from a delivered order: `POST /api/orders/{orderId}/returns`.
2. A manager reviews it: `GET /api/manager/returns`.
   - `REFUND_ONLY`: approve/reject immediately.
   - `RETURN_AND_REFUND`: first approval changes the request to `RETURN_AUTHORIZED`.
3. Warehouse staff inspect every requested item: `GET /api/warehouse/returns`, then `POST /api/warehouse/returns/{requestId}/inspection`. The inspection records received/condition/restock quantities and creates append-only `RETURN_RESTOCK` inventory movements for stock returned to inventory.
4. The manager reviews the inspection and approves/rejects the refund. Approval creates one idempotent `PENDING` refund.
5. A manager records completion with `POST /api/manager/refunds/{refundId}/complete`.

The customer return window uses `system_settings.return_window_days`. The order must be in `DELIVERED` state. Requested quantities are capped by the remaining unreturned quantity, and the refund amount is prorated from the order line after the order-level discount (shipping is not refunded).

## Support tickets

- Customer: `GET/POST /api/support/tickets`.
- Customer and support staff messages: `POST /api/support/tickets/{ticketId}/messages`.
- Support queue: `GET /api/support/tickets/queue`.
- Support status: `PATCH /api/support/tickets/{ticketId}/status`.

Customers can only read or reply to their own tickets. Closed tickets cannot receive further messages or status changes.

## Transaction reporting

- Dashboard data: `GET /api/manager/reports/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`.
- CSV export: `GET /api/manager/reports/export.csv?from=YYYY-MM-DD&to=YYYY-MM-DD`.

The reporting range is limited to 366 days and uses `Asia/Ho_Chi_Minh`. Order count is grouped by order creation date. Paid-order count and gross collected revenue are grouped by the actual successful QR payment timestamp or COD collection timestamp. Refunded amount/count include refunds recorded as `COMPLETED` during the selected date. Gross collected revenue is not netted against refunds; the refund total is reported separately.

## Important payment limitation

The existing application uses simulated QR payment and manual COD recording. The refund completion endpoint records an operator's confirmation in the database; it does **not** call a payment gateway or transfer real money. The frontend labels this limitation explicitly.

## New permissions

- `MANAGER_REFUND_COMPLETE`
- `MANAGER_REPORT_VIEW`
- `SUPPORT_TICKET_VIEW`
- `SUPPORT_TICKET_REPLY`

Existing `MANAGER_RETURN_DECIDE` and `WAREHOUSE_RETURN_INSPECT` permissions are reused.
