package com.osmshop.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PaymentFlowTests extends OrderTestSupport {
    @Autowired PaymentService payments;

    @Test
    void successfulQrPaymentConfirmsOrderAndCancellingCreatesSimulatedRefund() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(500000, 4, "ACTIVE");
        cart(customer.id(), Map.of(product, 2));
        String code = voucher("FIXED", 100000, null, 0, 5);
        String body = place(customer, address(customer.id()), "SIMULATED_QR", code)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(body).get("id").asLong();
        long attempt = latestAttempt(orderId);

        simulate(customer, attempt, "SUCCESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.paymentRetryAllowed").value(false));
        assertEquals("CONSUMED", voucherUsage(orderId));
        assertEquals(2, reserved(product), "stock stays reserved until the warehouse packs the order");
        simulate(customer, attempt, "SUCCESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPT_NOT_PENDING"));

        mvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.refund.status").value("PENDING"))
                .andExpect(jsonPath("$.refund.reason").value("CANCELLED_PAID_ORDER"))
                .andExpect(jsonPath("$.refund.amount").value(930000))
                .andExpect(jsonPath("$.refund.simulated").value(true));
        assertEquals(0, reserved(product));
        assertEquals("RELEASED", voucherUsage(orderId));
    }

    @Test
    void failedQrPaymentReleasesStockAndRetryReservesAgain() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(200000, 3, "ACTIVE");
        cart(customer.id(), Map.of(product, 3));
        String code = voucher("FIXED", 20000, null, 0, 5);
        String body = place(customer, address(customer.id()), "SIMULATED_QR", code)
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(body).get("id").asLong();

        simulate(customer, latestAttempt(orderId), "FAILURE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.paymentRetryAllowed").value(true));
        assertEquals(0, reserved(product));
        assertEquals("RELEASED", voucherUsage(orderId));

        retry(customer, orderId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.attemptNumber").value(2))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentAttemptsUsed").value(2))
                .andExpect(jsonPath("$.paymentRetryAllowed").value(false));
        assertEquals(3, reserved(product));
        assertEquals("RESERVED", voucherUsage(orderId));
        retry(customer, orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPT_ALREADY_PENDING"));
    }

    @Test
    void failingTheLastAllowedAttemptCancelsTheOrder() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(100000, 5, "ACTIVE");
        cart(customer.id(), Map.of(product, 1));
        long orderId = placedOrderId(customer, address(customer.id()), "SIMULATED_QR");

        simulate(customer, latestAttempt(orderId), "FAILURE").andExpect(status().isOk());
        retry(customer, orderId).andExpect(status().isCreated());
        simulate(customer, latestAttempt(orderId), "FAILURE").andExpect(status().isOk());
        retry(customer, orderId).andExpect(status().isCreated());
        simulate(customer, latestAttempt(orderId), "FAILURE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentAttemptsUsed").value(3))
                .andExpect(jsonPath("$.paymentRetryAllowed").value(false));
        assertEquals(0, reserved(product));
        retry(customer, orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_RETRY_NOT_ALLOWED"));
    }

    @Test
    void expiredQrCodesGiveStockBack() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(100000, 5, "ACTIVE");
        cart(customer.id(), Map.of(product, 2));
        long orderId = placedOrderId(customer, address(customer.id()), "SIMULATED_QR");
        long attempt = latestAttempt(orderId);
        expireLatestAttempt(orderId);

        simulate(customer, attempt, "SUCCESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPT_EXPIRED"));
        assertEquals("EXPIRED", jdbc.queryForObject(
                "SELECT status FROM payment_attempts WHERE id=?", String.class, attempt));
        assertEquals(0, reserved(product));
        retry(customer, orderId).andExpect(status().isCreated());
        assertEquals(2, reserved(product));

        // The background job expires overdue codes nobody touched.
        expireLatestAttempt(orderId);
        payments.expireDue();
        assertEquals("EXPIRED", jdbc.queryForObject(
                "SELECT status FROM payment_attempts WHERE id=?", String.class, latestAttempt(orderId)));
        assertEquals(0, reserved(product));
        assertEquals("AWAITING_PAYMENT", orderStatus(orderId));
    }

    @Test
    void retryRevalidatesPriceAndStock() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(100000, 2, "ACTIVE");
        cart(customer.id(), Map.of(product, 2));
        long orderId = placedOrderId(customer, address(customer.id()), "SIMULATED_QR");
        simulate(customer, latestAttempt(orderId), "FAILURE").andExpect(status().isOk());

        jdbc.update("UPDATE products SET stock_reserved = 1 WHERE id = ?", product);
        retry(customer, orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        jdbc.update("UPDATE products SET stock_reserved = 0, price = 120000 WHERE id = ?", product);
        retry(customer, orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
    }

    @Test
    void paymentsAreOwnedAndOnlyForQrOrders() throws Exception {
        Session owner = session("CUSTOMER");
        Session other = session("CUSTOMER");
        long product = product(100000, 5, "ACTIVE");
        cart(owner.id(), Map.of(product, 1));
        long qrOrder = placedOrderId(owner, address(owner.id()), "SIMULATED_QR");

        simulate(other, latestAttempt(qrOrder), "SUCCESS")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPT_NOT_FOUND"));
        retry(other, qrOrder).andExpect(status().isNotFound());
        assertEquals("AWAITING_PAYMENT", orderStatus(qrOrder));

        cart(owner.id(), Map.of(product, 1));
        long codOrder = placedOrderId(owner, address(owner.id()), "COD");
        simulate(owner, latestAttempt(codOrder), "SUCCESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPT_NOT_PENDING"));
        retry(owner, codOrder)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_RETRY_NOT_ALLOWED"));
        mvc.perform(post("/api/payment-attempts/" + latestAttempt(qrOrder) + "/simulate")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payment-attempts/" + latestAttempt(qrOrder) + "/simulate")
                        .header("Authorization", session("SUPPORT").bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions simulate(Session session, long attemptId, String outcome) throws Exception {
        return mvc.perform(post("/api/payment-attempts/" + attemptId + "/simulate")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"" + outcome + "\"}"));
    }

    private ResultActions retry(Session session, long orderId) throws Exception {
        return mvc.perform(post("/api/orders/" + orderId + "/payment-attempts").header("Authorization", session.bearer()));
    }
}
