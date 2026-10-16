/* DEV ONLY — put products into a customer's ACTIVE cart so checkout can be tested
   before the cart API (stream 2) exists. Run in pgAdmin Query Tool on database online_shop.

   Change the email and the SKU/quantity list below, then run the whole file.
   Running it again adds to the same cart (quantities are replaced, not summed). */
DO $$
DECLARE
    customer_email text := 'lan@example.test';
    wanted jsonb := '{"EL-002": 1, "SP-001": 2, "BK-003": 3}';
    v_customer_id bigint;
    v_cart_id bigint;
BEGIN
    SELECT id INTO v_customer_id FROM users WHERE email = lower(customer_email) AND role = 'CUSTOMER';
    IF v_customer_id IS NULL THEN
        RAISE EXCEPTION 'No CUSTOMER account with email %', customer_email;
    END IF;

    SELECT id INTO v_cart_id FROM carts WHERE user_id = v_customer_id AND status = 'ACTIVE';
    IF v_cart_id IS NULL THEN
        INSERT INTO carts(user_id) VALUES (v_customer_id) RETURNING id INTO v_cart_id;
    END IF;

    INSERT INTO cart_items(cart_id, product_id, quantity)
    SELECT v_cart_id, p.id, w.value::int
    FROM jsonb_each_text(wanted) w JOIN products p ON p.sku = w.key
    ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now();

    RAISE NOTICE 'Cart % of % now has % line(s)', v_cart_id, customer_email,
        (SELECT count(*) FROM cart_items ci WHERE ci.cart_id = v_cart_id);
END $$;
