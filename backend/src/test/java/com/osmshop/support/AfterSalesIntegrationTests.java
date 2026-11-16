package com.osmshop.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AfterSalesIntegrationTests extends AfterSalesIntegrationTestSupport {

    private Map<String, Object> returnRequest(String type, long itemId, int quantity) {
        return Map.of("requestType", type, "reason", "Item is not suitable", "items",
                List.of(Map.of("orderItemId", itemId, "quantity", quantity)));
    }

    @Test
    void customerCanOnlyRequestReturnForOwnDeliveredOrderWithinReturnWindow() throws Exception {
        Session customer = session("CUSTOMER");
        Session other = session("CUSTOMER");
        OrderFixture order = deliveredOrder(customer, 2, 10000);
        OrderFixture otherOrder = deliveredOrder(other, 1, 10000);

        call(post("/api/orders/" + otherOrder.id() + "/returns"), customer,
                returnRequest("REFUND_ONLY", otherOrder.itemId(), 1))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE orders SET status='CONFIRMED' WHERE id=?", order.id());
        call(post("/api/orders/" + order.id() + "/returns"), customer,
                returnRequest("REFUND_ONLY", order.itemId(), 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_ORDER_NOT_DELIVERED"));

        jdbc.update("UPDATE orders SET status='DELIVERED', delivered_at=now()-interval '10 days' WHERE id=?", order.id());
        call(post("/api/orders/" + order.id() + "/returns"), customer,
                returnRequest("REFUND_ONLY", order.itemId(), 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_WINDOW_EXPIRED"));
    }

    @Test
    void returnQuantityCannotExceedRemainingOrderQuantity() throws Exception {
        Session customer = session("CUSTOMER");
        OrderFixture order = deliveredOrder(customer, 3, 12000);
        call(post("/api/orders/" + order.id() + "/returns"), customer,
                returnRequest("REFUND_ONLY", order.itemId(), 2)).andExpect(status().isCreated());
        call(post("/api/orders/" + order.id() + "/returns"), customer,
                returnRequest("REFUND_ONLY", order.itemId(), 2))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_QUANTITY_EXCEEDED"));
    }

    @Test
    void returnAndRefundRequiresAuthorizationInspectionThenRefundCompletion() throws Exception {
        Session customer = session("CUSTOMER");
        Session manager = session("MANAGER");
        Session warehouse = session("WAREHOUSE");
        OrderFixture order = deliveredOrder(customer, 2, 15000);

        String created = call(post("/api/orders/" + order.id() + "/returns"), customer,
                returnRequest("RETURN_AND_REFUND", order.itemId(), 2))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long returnId = json.readTree(created).get("id").asLong();
        long returnItemId = json.readTree(created).get("items").get(0).get("id").asLong();

        call(post("/api/manager/returns/" + returnId + "/decision"), manager,
                Map.of("approved", true, "note", "Return authorized"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURN_AUTHORIZED"));
        call(post("/api/warehouse/returns/" + returnId + "/inspection"), warehouse,
                Map.of("items", List.of(Map.of("returnRequestItemId", returnItemId, "receivedQuantity", 2,
                        "conditionNote", "Unused and complete", "restockQuantity", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSPECTED"));
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM products WHERE id=?", Integer.class, order.productId()))
                .isEqualTo(11);

        call(post("/api/manager/returns/" + returnId + "/decision"), manager,
                Map.of("approved", true, "note", "Inspection accepted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_PENDING"));
        Long refundId = jdbc.queryForObject("SELECT id FROM refunds WHERE return_request_id=?", Long.class, returnId);
        call(post("/api/manager/refunds/" + refundId + "/complete"), manager, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT status FROM return_requests WHERE id=?", String.class, returnId))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT amount FROM refunds WHERE id=?", java.math.BigDecimal.class, refundId))
                .isEqualByComparingTo("30000.00");
    }

    @Test
    void refundOnlyCanBeApprovedAndCompletedWithoutWarehouseInspection() throws Exception {
        Session customer = session("CUSTOMER");
        Session manager = session("MANAGER");
        OrderFixture order = deliveredOrder(customer, 1, 27500);
        String created = call(post("/api/orders/" + order.id() + "/returns"), customer,
                returnRequest("REFUND_ONLY", order.itemId(), 1))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long returnId = json.readTree(created).get("id").asLong();
        call(post("/api/manager/returns/" + returnId + "/decision"), manager,
                Map.of("approved", true, "note", "Refund only approved"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_PENDING"));
        Long refundId = jdbc.queryForObject("SELECT id FROM refunds WHERE return_request_id=?", Long.class, returnId);
        call(post("/api/manager/refunds/" + refundId + "/complete"), manager, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT status FROM return_requests WHERE id=?", String.class, returnId))
                .isEqualTo("REFUNDED");
    }

    @Test
    void managerMustEnterNoteToRejectReturnRequest() throws Exception {
        Session customer = session("CUSTOMER");
        Session manager = session("MANAGER");
        OrderFixture order = deliveredOrder(customer, 1, 20000);
        String created = call(post("/api/orders/" + order.id() + "/returns"), customer,
                returnRequest("REFUND_ONLY", order.itemId(), 1))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long returnId = json.readTree(created).get("id").asLong();

        call(post("/api/manager/returns/" + returnId + "/decision"), manager, Map.of("approved", false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REJECT_NOTE_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM return_requests WHERE id=?", String.class, returnId))
                .isEqualTo("REQUESTED");

        call(post("/api/manager/returns/" + returnId + "/decision"), manager,
                Map.of("approved", false, "note", "Product was used"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Product was used"));
    }

    @Test
    void failedPrepaidDeliveryRefundIsCreatedOnceThenCompleted() throws Exception {
        Session customer = session("CUSTOMER");
        Session manager = session("MANAGER");
        OrderFixture order = deliveredOrder(customer, 2, 50000);
        jdbc.update("UPDATE orders SET status='RETURNED_TO_WAREHOUSE',delivered_at=NULL WHERE id=?", order.id());
        jdbc.update("""
            INSERT INTO payment_attempts(order_id,attempt_number,method,status,amount,simulation_code,expires_at,paid_at)
            VALUES (?,1,'SIMULATED_QR','PAID',100000,?,now()+interval '15 minutes',now())
            """, order.id(), "PAY-" + java.util.UUID.randomUUID());

        String candidates = call(get("/api/after-sales/failed-deliveries"), manager, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(candidates).contains("\"orderId\":" + order.id());

        String refund = call(post("/api/after-sales/failed-deliveries/" + order.id() + "/refund"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("FAILED_PREPAID_DELIVERY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(100000))
                .andReturn().getResponse().getContentAsString();
        long refundId = json.readTree(refund).get("id").asLong();

        call(post("/api/after-sales/failed-deliveries/" + order.id() + "/refund"), manager, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFUND_ALREADY_EXISTS"));
        call(post("/api/manager/refunds/" + refundId + "/complete"), manager, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void failedDeliveryRefundIsRefusedForCodAndForDeliveredOrders() throws Exception {
        Session customer = session("CUSTOMER");
        Session manager = session("MANAGER");
        OrderFixture delivered = deliveredOrder(customer, 1, 30000);
        call(post("/api/after-sales/failed-deliveries/" + delivered.id() + "/refund"), manager, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_RETURNED"));

        OrderFixture cod = deliveredOrder(customer, 1, 30000);
        jdbc.update("UPDATE orders SET status='RETURNED_TO_WAREHOUSE',payment_method='COD',delivered_at=NULL WHERE id=?", cod.id());
        call(post("/api/after-sales/failed-deliveries/" + cod.id() + "/refund"), manager, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_PREPAID"));
    }

    @Test
    void ticketOwnershipReplyAndStatusAreEnforced() throws Exception {
        Session customer = session("CUSTOMER");
        Session stranger = session("CUSTOMER");
        Session support = session("SUPPORT");
        String body = call(post("/api/support/tickets"), customer,
                Map.of("subject", "Delivery question", "message", "Where is my order?"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ticketId = json.readTree(body).get("id").asLong();

        call(post("/api/support/tickets/" + ticketId + "/messages"), stranger,
                Map.of("message", "I should not see this ticket"))
                .andExpect(status().isNotFound());
        call(post("/api/support/tickets/" + ticketId + "/messages"), support,
                Map.of("message", "We are checking this for you"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        call(patch("/api/support/tickets/" + ticketId + "/status"), support,
                Map.of("status", "RESOLVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_messages WHERE ticket_id=?", Integer.class, ticketId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM support_tickets WHERE id=?", String.class, ticketId))
                .isEqualTo("RESOLVED");
    }

    @Test
    void moduleFiveEndpointsReturn403WithoutRequiredPermissions() throws Exception {
        Session customer = session("CUSTOMER");
        OrderFixture order = deliveredOrder(customer, 1, 10000);
        call(get("/api/manager/returns"), customer, null).andExpect(status().isForbidden());
        call(get("/api/warehouse/returns"), customer, null).andExpect(status().isForbidden());
        call(get("/api/support/tickets/queue"), customer, null).andExpect(status().isForbidden());
        call(get("/api/manager/reports/summary?from=2026-11-01&to=2026-11-03"), customer, null)
                .andExpect(status().isForbidden());
        call(get("/api/manager/reports/dashboard"), customer, null).andExpect(status().isForbidden());
        call(get("/api/manager/reports/daily-reports"), customer, null).andExpect(status().isForbidden());
        call(get("/api/after-sales/failed-deliveries"), customer, null).andExpect(status().isForbidden());
        call(post("/api/after-sales/failed-deliveries/" + order.id() + "/refund"), customer, null)
                .andExpect(status().isForbidden());
    }
}
