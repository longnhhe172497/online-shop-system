package com.osmshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end order fulfilment across roles: Customer checkout → Support COD confirmation → Warehouse packing
 * → Manager assignment → Delivery → Customer tracking. Every step goes through the real HTTP API.
 */
class FulfillmentFlowTests extends OrderTestSupport {
    @Autowired EntityManager entityManager;

    /** The logistics module uses JPA; clearing the persistence context mimics a new request transaction. */
    private void nextRequest() {
        entityManager.flush();
        entityManager.clear();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               Session session, Object body) throws Exception {
        request.header("Authorization", session.bearer());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        ResultActions result = mvc.perform(request);
        nextRequest();
        return result;
    }

    private long codOrder(Session customer, long product, int quantity, String voucher) throws Exception {
        cart(customer.id(), Map.of(product, quantity));
        String body = place(customer, address(customer.id()), "COD", voucher)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        nextRequest();
        return json.readTree(body).get("id").asLong();
    }

    private long shipmentId(Session shipper, long orderId) throws Exception {
        String body = send(get("/api/internal/delivery/shipments?scope=MINE&size=100"), shipper, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode item : json.readTree(body).get("items")) {
            if (item.get("orderId").asLong() == orderId) return item.get("id").asLong();
        }
        throw new AssertionError("Shipment of order " + orderId + " is not in the shipper's queue");
    }

    @Test
    void codOrderTravelsFromCheckoutToDeliveredAndPaid() throws Exception {
        Session customer = session("CUSTOMER");
        Session support = session("SUPPORT");
        Session warehouse = session("WAREHOUSE");
        Session manager = session("MANAGER");
        Session shipper = session("DELIVERY");
        long product = product(250000, 5, "ACTIVE");
        String code = voucher("FIXED", 50000, null, 0, 5);
        long orderId = codOrder(customer, product, 2, code);
        assertThat(reserved(product)).isEqualTo(2);

        // The warehouse cannot pack an unconfirmed COD order.
        send(post("/api/internal/warehouse/orders/" + orderId + "/pack"), warehouse, Map.of())
                .andExpect(status().isConflict());

        // 1. Support confirms the COD order
        send(get("/api/support/orders/pending?size=100"), support, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem((int) orderId)));
        send(post("/api/support/orders/" + orderId + "/confirm"), support, Map.of("note", "Đã gọi xác nhận"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedBy").value("Test User"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        assertThat(reserved(product)).isEqualTo(2);

        // 2. Warehouse packs: on-hand stock drops once, the reservation is consumed
        send(post("/api/internal/warehouse/orders/" + orderId + "/pack"), warehouse, Map.of())
                .andExpect(status().isCreated());
        assertThat(orderStatus(orderId)).isEqualTo("READY_FOR_DELIVERY");
        assertThat(onHand(product)).isEqualTo(3);
        assertThat(reserved(product)).isZero();

        // 3. Manager finds the order and assigns a shipper
        String orderCode = jdbc.queryForObject("SELECT order_code FROM orders WHERE id = ?", String.class, orderId);
        send(get("/api/manager/orders?status=READY_FOR_DELIVERY&search=" + orderCode), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].shipmentStatus").value("READY"));
        send(get("/api/manager/delivery-staff"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) shipper.id())));
        send(post("/api/manager/orders/" + orderId + "/assign"), manager, Map.of("deliveryStaffId", shipper.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.shipment.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.shipment.deliveryStaffName").value("Test User"));

        // 4. The shipper sees it, starts delivery and delivers with COD collected
        long shipmentId = shipmentId(shipper, orderId);
        send(patch("/api/internal/delivery/shipments/" + shipmentId + "/status"), shipper, Map.of("status", "SHIPPING"))
                .andExpect(status().isOk());
        assertThat(orderStatus(orderId)).isEqualTo("DELIVERING");
        send(patch("/api/internal/delivery/shipments/" + shipmentId + "/status"), shipper,
                Map.of("status", "DELIVERED", "codCollected", true))
                .andExpect(status().isOk());
        assertThat(orderStatus(orderId)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_attempts WHERE order_id = ?", String.class, orderId))
                .isEqualTo("PAID");
        assertThat(voucherUsage(orderId)).isEqualTo("CONSUMED");

        // 5. The customer tracks the delivered parcel
        send(get("/api/orders/" + orderId + "/shipment"), customer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRK-" + orderCode))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveryStaffName").value("Test User"))
                .andExpect(jsonPath("$.codCollectedAt").isNotEmpty())
                .andExpect(jsonPath("$.attempts[0].status").value("DELIVERED"));
        send(get("/api/orders/" + orderId), customer, null)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty())
                .andExpect(jsonPath("$.cancellable").value(false));
    }

    @Test
    void paidQrOrderIsPackedAssignedAndDeliveredWithoutCod() throws Exception {
        Session customer = session("CUSTOMER");
        Session manager = session("MANAGER");
        Session shipper = session("DELIVERY");
        long product = product(300000, 4, "ACTIVE");
        cart(customer.id(), Map.of(product, 1));
        String body = place(customer, address(customer.id()), "SIMULATED_QR", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        nextRequest();
        long orderId = json.readTree(body).get("id").asLong();
        send(post("/api/payment-attempts/" + latestAttempt(orderId) + "/simulate"), customer, Map.of("outcome", "SUCCESS"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        send(get("/api/orders/" + orderId + "/shipment"), customer, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIPMENT_NOT_FOUND"));
        send(post("/api/internal/warehouse/orders/" + orderId + "/pack"), session("WAREHOUSE"), Map.of())
                .andExpect(status().isCreated());
        send(post("/api/manager/orders/" + orderId + "/assign"), manager, Map.of("deliveryStaffId", shipper.id()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
        long shipmentId = shipmentId(shipper, orderId);
        send(patch("/api/internal/delivery/shipments/" + shipmentId + "/status"), shipper, Map.of("status", "SHIPPING"))
                .andExpect(status().isOk());
        send(get("/api/orders/" + orderId + "/shipment"), customer, null)
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.codAmount").value(0));
        send(patch("/api/internal/delivery/shipments/" + shipmentId + "/status"), shipper, Map.of("status", "DELIVERED"))
                .andExpect(status().isOk());
        assertThat(orderStatus(orderId)).isEqualTo("DELIVERED");
    }

    @Test
    void supportCancelsAnUnreachableCodOrderAndStockReturns() throws Exception {
        Session customer = session("CUSTOMER");
        Session support = session("SUPPORT");
        long product = product(100000, 5, "ACTIVE");
        long orderId = codOrder(customer, product, 3, null);

        send(post("/api/support/orders/" + orderId + "/cancel"), support, Map.of("reason", " "))
                .andExpect(status().isBadRequest());
        send(post("/api/support/orders/" + orderId + "/cancel"), support, Map.of("reason", "Không liên lạc được khách"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.note").value("Hỗ trợ hủy đơn: Không liên lạc được khách"));
        assertThat(reserved(product)).isZero();
        send(post("/api/support/orders/" + orderId + "/confirm"), support, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING_CONFIRMATION"));
    }

    @Test
    void supportOnlyConfirmsCodOrdersWaitingForConfirmation() throws Exception {
        Session customer = session("CUSTOMER");
        Session support = session("SUPPORT");
        cart(customer.id(), Map.of(product(100000, 5, "ACTIVE"), 1));
        long qrOrder = placedOrderId(customer, address(customer.id()), "SIMULATED_QR");
        nextRequest();
        send(post("/api/support/orders/" + qrOrder + "/confirm"), support, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING_CONFIRMATION"));
        send(post("/api/support/orders/999999999/confirm"), support, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void managerAssignmentRulesAreEnforced() throws Exception {
        // This scenario requires one retry after the first failed delivery and must not depend on
        // a mutable value left in the developer database by an administrator.
        jdbc.update("UPDATE system_settings SET value_text='2' WHERE setting_key='max_delivery_attempts'");
        Session customer = session("CUSTOMER");
        Session support = session("SUPPORT");
        Session manager = session("MANAGER");
        Session first = session("DELIVERY");
        Session second = session("DELIVERY");
        long orderId = codOrder(customer, product(100000, 5, "ACTIVE"), 1, null);

        send(post("/api/manager/orders/" + orderId + "/assign"), manager, Map.of("deliveryStaffId", first.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_ASSIGNABLE"));
        send(post("/api/support/orders/" + orderId + "/confirm"), support, null).andExpect(status().isOk());
        send(post("/api/internal/warehouse/orders/" + orderId + "/pack"), session("WAREHOUSE"), Map.of())
                .andExpect(status().isCreated());

        send(post("/api/manager/orders/" + orderId + "/assign"), manager, Map.of("deliveryStaffId", customer.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_DELIVERY_STAFF"));
        send(post("/api/manager/orders/" + orderId + "/assign"), manager, Map.of("deliveryStaffId", first.id()))
                .andExpect(status().isOk());
        // Reassigning before the parcel leaves is allowed
        send(post("/api/manager/orders/" + orderId + "/assign"), manager, Map.of("deliveryStaffId", second.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("ASSIGNED"));
        assertThat(jdbc.queryForObject("SELECT assigned_staff_id FROM shipments WHERE order_id = ?", Long.class, orderId))
                .isEqualTo(second.id());

        long shipmentId = shipmentId(second, orderId);
        send(patch("/api/internal/delivery/shipments/" + shipmentId + "/status"), second, Map.of("status", "SHIPPING"))
                .andExpect(status().isOk());
        send(post("/api/manager/orders/" + orderId + "/assign"), manager, Map.of("deliveryStaffId", first.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_ASSIGNABLE"));

        // After a failed attempt the manager may hand the parcel to another shipper
        send(patch("/api/internal/delivery/shipments/" + shipmentId + "/status"), second,
                Map.of("status", "DELIVERY_FAILED", "failedReason", "Khách vắng nhà"))
                .andExpect(status().isOk());
        send(post("/api/manager/orders/" + orderId + "/assign"), manager, Map.of("deliveryStaffId", first.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERY_FAILED"))
                .andExpect(jsonPath("$.shipment.attempts[0].status").value("FAILED"));
        send(get("/api/orders/" + orderId + "/shipment"), customer, null)
                .andExpect(jsonPath("$.attempts[0].failureReason").value("Khách vắng nhà"));
    }

    @Test
    void managerListsAndFiltersOrders() throws Exception {
        Session customer = session("CUSTOMER");
        Session manager = session("MANAGER");
        long orderId = codOrder(customer, product(100000, 5, "ACTIVE"), 2, null);
        String orderCode = jdbc.queryForObject("SELECT order_code FROM orders WHERE id = ?", String.class, orderId);

        send(get("/api/manager/orders?search=" + orderCode.toLowerCase()), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(orderId))
                .andExpect(jsonPath("$.items[0].itemCount").value(2))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("UNPAID"));
        send(get("/api/manager/orders?status=DELIVERED&search=" + orderCode), manager, null)
                .andExpect(jsonPath("$.totalItems").value(0));
        send(get("/api/manager/orders?status=SHIPPED"), manager, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
        send(get("/api/manager/orders/" + orderId), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerEmail").isNotEmpty())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.assignable").value(false));
        send(get("/api/manager/orders/999999999"), manager, null).andExpect(status().isNotFound());
    }

    @Test
    void confirmationEmailTellsTheCustomerTheCodOrderIsConfirmed(@Autowired OrderMailer mailer) throws Exception {
        long orderId = codOrder(session("CUSTOMER"), product(100000, 5, "ACTIVE"), 1, null);

        mailer.send(new OrderEvent(orderId, OrderEvent.Kind.CONFIRMED));

        org.mockito.ArgumentCaptor<org.springframework.mail.SimpleMailMessage> sent =
                org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(mail).send(sent.capture());
        assertThat(sent.getValue().getSubject()).endsWith("đã được xác nhận");
        assertThat(sent.getValue().getText()).contains("thanh toán khi nhận hàng (COD)");
    }

    @Test
    void staffEndpointsRequireTheirPermissions() throws Exception {
        Session customer = session("CUSTOMER");
        long orderId = codOrder(customer, product(100000, 5, "ACTIVE"), 1, null);
        for (String role : new String[] {"ADMIN", "MANAGER", "CUSTOMER"}) {
            send(get("/api/support/orders/pending"), session(role), null).andExpect(status().isForbidden());
            send(post("/api/support/orders/" + orderId + "/confirm"), session(role), null).andExpect(status().isForbidden());
        }
        for (String role : new String[] {"ADMIN", "SUPPORT", "CUSTOMER"}) {
            send(get("/api/manager/orders"), session(role), null).andExpect(status().isForbidden());
            send(get("/api/manager/delivery-staff"), session(role), null).andExpect(status().isForbidden());
        }
        jdbc.update("DELETE FROM role_permissions WHERE role='MANAGER' AND permission_code='MANAGER_ORDER_ASSIGN'");
        send(post("/api/manager/orders/" + orderId + "/assign"), session("MANAGER"), Map.of("deliveryStaffId", 1))
                .andExpect(status().isForbidden());
        send(get("/api/orders/" + orderId + "/shipment"), session("CUSTOMER"), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_CONFIRMATION");
    }
}
