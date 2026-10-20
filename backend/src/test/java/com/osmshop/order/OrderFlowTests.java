package com.osmshop.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class OrderFlowTests extends OrderTestSupport {

    @Test
    void qrCheckoutReservesStockAndConvertsCart() throws Exception {
        Session customer = session("CUSTOMER");
        long address = address(customer.id());
        long keyboard = product(650000, 10, "ACTIVE");
        long mouse = product(150000, 5, "ACTIVE");
        cart(customer.id(), Map.of(keyboard, 1, mouse, 2));

        String body = place(customer, address, "SIMULATED_QR", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.paymentMethod").value("SIMULATED_QR"))
                .andExpect(jsonPath("$.subtotal").value(950000))
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.shippingFee").value(30000))
                .andExpect(jsonPath("$.totalAmount").value(980000))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.payment.simulated").value(true))
                .andExpect(jsonPath("$.payment.simulationCode").isNotEmpty())
                .andExpect(jsonPath("$.payment.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.paymentAttemptsUsed").value(1))
                .andExpect(jsonPath("$.maxPaymentAttempts").value(3))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.paymentRetryAllowed").value(false))
                .andExpect(jsonPath("$.shippingAddress").value("1 Lê Lợi, Bến Nghé, Quận 1, TP. Hồ Chí Minh"))
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(body).get("id").asLong();

        assertEquals(1, reserved(keyboard));
        assertEquals(2, reserved(mouse));
        assertEquals(10, onHand(keyboard), "checkout must not reduce on-hand stock");
        assertEquals("CONVERTED", jdbc.queryForObject(
                "SELECT status FROM carts WHERE user_id=?", String.class, customer.id()));
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM stock_reservations r JOIN order_items i ON i.id = r.order_item_id
                WHERE i.order_id = ? AND r.status = 'ACTIVE' AND r.payment_attempt_id IS NOT NULL
                  AND r.expires_at IS NOT NULL
                """, Integer.class, orderId));

        mvc.perform(get("/api/orders").header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(orderId))
                .andExpect(jsonPath("$.items[0].itemCount").value(3));
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCode").isNotEmpty());

        // A second submit finds no ACTIVE cart, so stock is never reserved twice.
        place(customer, address, "SIMULATED_QR", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
        assertEquals(1, reserved(keyboard));
    }

    @Test
    void codCheckoutWaitsForSupportConfirmation() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(200000, 3, "ACTIVE");
        cart(customer.id(), Map.of(product, 3));

        place(customer, address(customer.id()), "COD", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.payment.method").value("COD"))
                .andExpect(jsonPath("$.payment.status").value("UNPAID"))
                .andExpect(jsonPath("$.payment.simulated").value(false))
                .andExpect(jsonPath("$.payment.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.paymentRetryAllowed").value(false));
        assertEquals(3, reserved(product));
    }

    @Test
    void previewQuotesTheCartWithoutReservingAnything() throws Exception {
        Session customer = session("CUSTOMER");
        mvc.perform(post("/api/orders/preview").header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.canPlaceOrder").value(false));

        long ok = product(300000, 5, "ACTIVE");
        long scarce = product(100000, 1, "ACTIVE");
        cart(customer.id(), Map.of(ok, 1, scarce, 2));
        String code = voucher("FIXED", 50000, null, 0, 5);
        mvc.perform(post("/api/orders/preview").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"voucherCode\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(500000))
                .andExpect(jsonPath("$.discountAmount").value(50000))
                .andExpect(jsonPath("$.shippingFee").value(30000))
                .andExpect(jsonPath("$.totalAmount").value(480000))
                .andExpect(jsonPath("$.voucherCode").value(code))
                .andExpect(jsonPath("$.canPlaceOrder").value(false))
                // Lines are ordered by product id; "ok" was created first.
                .andExpect(jsonPath("$.items[0].productId").value(ok))
                .andExpect(jsonPath("$.items[0].problem").doesNotExist())
                .andExpect(jsonPath("$.items[1].problem").value("INSUFFICIENT_STOCK"));
        assertEquals(0, reserved(ok));
        mvc.perform(post("/api/orders/preview").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"voucherCode\":\"NOPE\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VOUCHER_INVALID"));
    }

    @Test
    void voucherDiscountIsCappedAndSplitAcrossLines() throws Exception {
        Session customer = session("CUSTOMER");
        long a = product(333333, 5, "ACTIVE");
        long b = product(100001, 5, "ACTIVE");
        cart(customer.id(), Map.of(a, 1, b, 1));
        String code = voucher("PERCENT", 50, 120000, 300000, 10);

        String body = place(customer, address(customer.id()), "SIMULATED_QR", code.toLowerCase())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voucherCode").value(code))
                .andExpect(jsonPath("$.discountAmount").value(120000))
                .andExpect(jsonPath("$.totalAmount").value(433334 - 120000 + 30000))
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(body).get("id").asLong();

        List<BigDecimal> lineDiscounts = jdbc.queryForList(
                "SELECT discount_amount FROM order_items WHERE order_id=?", BigDecimal.class, orderId);
        assertEquals(0, lineDiscounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("120000")));
        lineDiscounts.forEach(amount -> assertEquals(0, amount.stripTrailingZeros().scale() <= 0 ? 0 : 1,
                "line discounts are whole đồng: " + amount));
        assertEquals("RESERVED", voucherUsage(orderId));
    }

    @Test
    void voucherRulesAreEnforced() throws Exception {
        Session customer = session("CUSTOMER");
        long address = address(customer.id());
        long product = product(100000, 5, "ACTIVE");
        cart(customer.id(), Map.of(product, 1));

        place(customer, address, "COD", "NO-SUCH-CODE")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VOUCHER_INVALID"));
        place(customer, address, "COD", voucher("FIXED", 50000, null, 300000, 10))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VOUCHER_MIN_ORDER_NOT_MET"));
        String inactive = voucher("FIXED", 10000, null, 0, 10);
        jdbc.update("UPDATE vouchers SET status='INACTIVE' WHERE code=?", inactive);
        place(customer, address, "COD", inactive)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VOUCHER_NOT_ACTIVE"));
        String usedUp = voucher("FIXED", 10000, null, 0, 1);
        jdbc.update("""
                INSERT INTO voucher_usages(voucher_id,order_id,status)
                VALUES ((SELECT id FROM vouchers WHERE code=?), ?, 'CONSUMED')
                """, usedUp, otherOrder());
        place(customer, address, "COD", usedUp)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_USAGE_LIMIT_REACHED"));
        assertEquals(0, reserved(product), "failed checkout must not reserve stock");
    }

    @Test
    void stockAndAvailabilityAreRechecked() throws Exception {
        Session customer = session("CUSTOMER");
        long address = address(customer.id());
        long scarce = product(100000, 2, "ACTIVE");
        jdbc.update("UPDATE products SET stock_reserved = 1 WHERE id = ?", scarce);
        cart(customer.id(), Map.of(scarce, 2));
        place(customer, address, "COD", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM carts WHERE user_id=?", String.class, customer.id()));

        jdbc.update("UPDATE products SET stock_reserved = 0, status = 'INACTIVE' WHERE id = ?", scarce);
        place(customer, address, "COD", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
    }

    @Test
    void cancellingBeforePackingReturnsStockAndVoucher() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(400000, 5, "ACTIVE");
        cart(customer.id(), Map.of(product, 2));
        String code = voucher("FIXED", 50000, null, 0, 5);
        String body = place(customer, address(customer.id()), "COD", code)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(body).get("id").asLong();
        assertEquals(2, reserved(product));

        mvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Đặt nhầm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.payment.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refund").doesNotExist())
                .andExpect(jsonPath("$.cancellable").value(false));
        assertEquals(0, reserved(product));
        assertEquals("RELEASED", voucherUsage(orderId));
        mvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", customer.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void packedOrdersCannotBeCancelled() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(100000, 5, "ACTIVE");
        cart(customer.id(), Map.of(product, 1));
        long orderId = placedOrderId(customer, address(customer.id()), "COD");
        jdbc.update("UPDATE orders SET status = 'READY_FOR_DELIVERY' WHERE id = ?", orderId);
        mvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", customer.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void customersSeeOnlyTheirOwnOrdersAndAddresses() throws Exception {
        Session owner = session("CUSTOMER");
        Session other = session("CUSTOMER");
        long product = product(100000, 5, "ACTIVE");
        cart(owner.id(), Map.of(product, 1));
        cart(other.id(), Map.of(product, 1));

        place(other, address(owner.id()), "COD", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        long orderId = placedOrderId(owner, address(owner.id()), "COD");

        mvc.perform(get("/api/orders/" + orderId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").header("Authorization", other.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        assertEquals("PENDING_CONFIRMATION", orderStatus(orderId));
    }

    @Test
    void onlyCustomersCanCheckoutAndRequestsAreValidated() throws Exception {
        Session customer = session("CUSTOMER");
        long address = address(customer.id());
        place(customer, address, "COD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
        place(customer, address, "BANK_TRANSFER", null).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders?size=101").header("Authorization", customer.bearer()))
                .andExpect(status().isBadRequest());

        Session manager = session("MANAGER");
        mvc.perform(get("/api/orders").header("Authorization", manager.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/preview").header("Authorization", manager.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
}
