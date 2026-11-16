-- Read-only assertions for database/seed/dev_full_demo.sql.
-- The script raises an exception when a required demo scenario is missing or inconsistent.

DO $$
DECLARE
    missing text;
BEGIN
    SELECT string_agg(required_role, ', ' ORDER BY required_role) INTO missing
    FROM (VALUES ('ADMIN'),('MANAGER'),('SUPPORT'),('WAREHOUSE'),('DELIVERY'),('CUSTOMER')) r(required_role)
    WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.role=r.required_role AND u.status='ACTIVE');
    IF missing IS NOT NULL THEN RAISE EXCEPTION 'Missing active roles: %', missing; END IF;

    IF (SELECT count(*) FROM products WHERE status='ACTIVE') < 10 THEN
        RAISE EXCEPTION 'Expected at least 10 active products';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM products WHERE stock_on_hand-stock_reserved<=reorder_threshold) THEN
        RAISE EXCEPTION 'Missing low-stock product scenario';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM carts WHERE status='ACTIVE' AND user_id IS NOT NULL) OR
       NOT EXISTS (SELECT 1 FROM carts WHERE status='ACTIVE' AND guest_session_hash IS NOT NULL) THEN
        RAISE EXCEPTION 'Missing active customer or guest cart';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM vouchers WHERE status='ACTIVE' AND valid_from<=now() AND valid_until>now()) THEN
        RAISE EXCEPTION 'Missing usable voucher';
    END IF;

    SELECT string_agg(required_status, ', ' ORDER BY required_status) INTO missing
    FROM (VALUES ('AWAITING_PAYMENT'),('PENDING_CONFIRMATION'),('CONFIRMED'),('READY_FOR_DELIVERY'),
                 ('ASSIGNED'),('DELIVERING'),('DELIVERED'),('DELIVERY_FAILED'),
                 ('RETURNED_TO_WAREHOUSE'),('CANCELLED')) s(required_status)
    WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.status=s.required_status);
    IF missing IS NOT NULL THEN RAISE EXCEPTION 'Missing order statuses: %', missing; END IF;

    IF NOT EXISTS (SELECT 1 FROM payment_attempts WHERE method='SIMULATED_QR' AND status='PENDING') OR
       NOT EXISTS (SELECT 1 FROM payment_attempts WHERE method='SIMULATED_QR' AND status='FAILED') OR
       NOT EXISTS (SELECT 1 FROM payment_attempts WHERE method='SIMULATED_QR' AND status='PAID') OR
       NOT EXISTS (SELECT 1 FROM payment_attempts WHERE method='COD' AND status='UNPAID') OR
       NOT EXISTS (SELECT 1 FROM payment_attempts WHERE method='COD' AND status='PAID') THEN
        RAISE EXCEPTION 'Missing payment scenario';
    END IF;

    SELECT string_agg(required_status, ', ' ORDER BY required_status) INTO missing
    FROM (VALUES ('READY'),('ASSIGNED'),('IN_TRANSIT'),('DELIVERED'),('DELIVERY_FAILED'),
                 ('RETURNED_TO_WAREHOUSE')) s(required_status)
    WHERE NOT EXISTS (SELECT 1 FROM shipments sh WHERE sh.status=s.required_status);
    IF missing IS NOT NULL THEN RAISE EXCEPTION 'Missing shipment statuses: %', missing; END IF;

    SELECT string_agg(required_status, ', ' ORDER BY required_status) INTO missing
    FROM (VALUES ('REQUESTED'),('RETURN_AUTHORIZED'),('INSPECTED'),('REFUND_PENDING'),
                 ('REFUNDED'),('REJECTED')) s(required_status)
    WHERE NOT EXISTS (SELECT 1 FROM return_requests rr WHERE rr.status=s.required_status);
    IF missing IS NOT NULL THEN RAISE EXCEPTION 'Missing return statuses: %', missing; END IF;

    SELECT string_agg(required_status, ', ' ORDER BY required_status) INTO missing
    FROM (VALUES ('OPEN'),('IN_PROGRESS'),('RESOLVED'),('CLOSED')) s(required_status)
    WHERE NOT EXISTS (SELECT 1 FROM support_tickets t WHERE t.status=s.required_status);
    IF missing IS NOT NULL THEN RAISE EXCEPTION 'Missing ticket statuses: %', missing; END IF;

    IF NOT EXISTS (SELECT 1 FROM product_reviews WHERE status='VISIBLE') OR
       NOT EXISTS (SELECT 1 FROM product_reviews WHERE status='HIDDEN') OR
       NOT EXISTS (SELECT 1 FROM product_reviews WHERE manager_reply IS NOT NULL) THEN
        RAISE EXCEPTION 'Missing review moderation scenario';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM refunds WHERE status='PENDING') OR
       NOT EXISTS (SELECT 1 FROM refunds WHERE status='COMPLETED') THEN
        RAISE EXCEPTION 'Missing refund scenario';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE status='PENDING_VERIFICATION') OR
       NOT EXISTS (SELECT 1 FROM users WHERE status='LOCKED') OR
       NOT EXISTS (SELECT 1 FROM users WHERE status='INACTIVE') THEN
        RAISE EXCEPTION 'Missing account status scenario';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM auth_tokens WHERE purpose='EMAIL_VERIFICATION' AND used_at IS NULL AND expires_at>now()) OR
       NOT EXISTS (SELECT 1 FROM auth_tokens WHERE purpose='PASSWORD_RESET' AND used_at IS NULL AND expires_at>now()) OR
       NOT EXISTS (SELECT 1 FROM staff_invitations WHERE accepted_at IS NULL AND revoked_at IS NULL AND expires_at>now()) THEN
        RAISE EXCEPTION 'Missing usable auth token or staff invitation';
    END IF;

    IF EXISTS (
        SELECT 1 FROM products p
        WHERE p.stock_reserved <> COALESCE((
            SELECT sum(sr.quantity)::integer FROM stock_reservations sr
            JOIN order_items oi ON oi.id=sr.order_item_id
            WHERE oi.product_id=p.id AND sr.status='ACTIVE'
        ),0)
    ) THEN RAISE EXCEPTION 'Product reserved stock does not match active reservations'; END IF;

    RAISE NOTICE 'Full demo dataset verification passed.';
END $$;

SELECT role,status,count(*) AS accounts FROM users GROUP BY role,status ORDER BY role,status;
SELECT status,count(*) AS orders FROM orders GROUP BY status ORDER BY status;
SELECT status,count(*) AS returns FROM return_requests GROUP BY status ORDER BY status;
SELECT status,count(*) AS tickets FROM support_tickets GROUP BY status ORDER BY status;
