package com.osmshop.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.osmshop.auth.AuthApiException;
import com.osmshop.support.AfterSalesDtos.CreateReturnRequest;
import com.osmshop.support.AfterSalesDtos.InspectionRequest;
import com.osmshop.support.AfterSalesDtos.ItemInspection;
import com.osmshop.support.AfterSalesDtos.RefundView;
import com.osmshop.support.AfterSalesDtos.ReturnDecisionRequest;
import com.osmshop.support.AfterSalesDtos.ReturnItemRequest;
import com.osmshop.support.AfterSalesDtos.ReturnView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Pure service tests for return requests, inspection and refunds (UC-CUS-014, UC-WH-002, UC-MNG-004):
 * no Spring context, HTTP layer or database.
 */
class AfterSalesServiceUnitTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AfterSalesService service = new AfterSalesService(jdbc);

    // ---------- createReturn (UC-CUS-014) ----------

    @Test
    void createReturn_whenRequestTypeUnknown_rejectsBeforeQuery() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.createReturn(5L, 20L, returnRequest("EXCHANGE", 1)));

        assertEquals("INVALID_RETURN_TYPE", error.code());
        verifyNoInteractions(jdbc);
    }

    @Test
    void createReturn_whenOrderNotOwned_throws404() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.createReturn(5L, 20L, returnRequest("REFUND_ONLY", 1)));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
    }

    @Test
    void createReturn_whenOrderNotDelivered_throws409() throws Exception {
        stubCustomerOrder("DELIVERING", null);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.createReturn(5L, 20L, returnRequest("REFUND_ONLY", 1)));

        assertEquals("RETURN_ORDER_NOT_DELIVERED", error.code());
        verify(jdbc, never()).queryForObject(contains("INSERT INTO return_requests"), eq(Long.class),
                any(), any(), any(), any(), any());
    }

    @Test
    void createReturn_whenReturnWindowExpired_throws409() throws Exception {
        stubCustomerOrder("DELIVERED", OffsetDateTime.now().minusDays(10));
        when(jdbc.queryForList(contains("return_window_days"), eq(String.class))).thenReturn(List.of("7"));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.createReturn(5L, 20L, returnRequest("REFUND_ONLY", 1)));

        assertEquals("RETURN_WINDOW_EXPIRED", error.code());
    }

    @Test
    void createReturn_whenWindowSettingMissing_usesSevenDays() throws Exception {
        stubCustomerOrder("DELIVERED", OffsetDateTime.now().minusDays(8));
        when(jdbc.queryForList(contains("return_window_days"), eq(String.class))).thenReturn(List.of());

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.createReturn(5L, 20L, returnRequest("REFUND_ONLY", 1)));

        assertEquals("RETURN_WINDOW_EXPIRED", error.code());
    }

    @Test
    void createReturn_whenQuantityExceedsRemaining_throws409() throws Exception {
        stubCustomerOrder("DELIVERED", OffsetDateTime.now().minusDays(1));
        when(jdbc.queryForList(contains("return_window_days"), eq(String.class))).thenReturn(List.of("7"));
        when(jdbc.queryForObject(contains("INSERT INTO return_requests"), eq(Long.class),
                any(), any(), any(), any(), any())).thenReturn(30L);
        ResultSet item = mock(ResultSet.class);
        when(item.getInt(1)).thenReturn(2);
        when(item.getBigDecimal(2)).thenReturn(new BigDecimal("200000"));
        when(item.getBigDecimal(3)).thenReturn(new BigDecimal("200000"));
        when(item.getBigDecimal(4)).thenReturn(BigDecimal.ZERO);
        when(jdbc.query(contains("FROM order_items oi JOIN orders o"), ArgumentMatchers.<RowMapper<Object>>any(), any(), any()))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(item, 0)));
        when(jdbc.queryForObject(contains("FROM return_request_items ri JOIN return_requests rr"), eq(Integer.class), any()))
                .thenReturn(2);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.createReturn(5L, 20L, returnRequest("REFUND_ONLY", 1)));

        assertEquals("RETURN_QUANTITY_EXCEEDED", error.code());
    }

    // ---------- decideReturn (UC-MNG-004) ----------

    @Test
    void decideReturn_whenRejectedWithoutNote_rejectsBeforeQuery() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.decideReturn(1L, 7L, new ReturnDecisionRequest(false, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertEquals("REJECT_NOTE_REQUIRED", error.code());
        verifyNoInteractions(jdbc);
    }

    @Test
    void decideReturn_whenRejectedWithBlankNote_rejectsBeforeQuery() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.decideReturn(1L, 7L, new ReturnDecisionRequest(false, "   ")));

        assertEquals("REJECT_NOTE_REQUIRED", error.code());
        verifyNoInteractions(jdbc);
    }

    @Test
    void decideReturn_whenRequestMissing_throws404() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.decideReturn(1L, 7L, new ReturnDecisionRequest(true, null)));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
    }

    @Test
    void decideReturn_whenAlreadyRejected_throws409() throws Exception {
        stubReturnHead("REFUND_ONLY", "REJECTED", "SIMULATED_QR");

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.decideReturn(1L, 7L, new ReturnDecisionRequest(true, null)));

        assertEquals("RETURN_ALREADY_DECIDED", error.code());
    }

    @Test
    void decideReturn_whenRejectedWithNote_savesRejectedStatusAndAudits() throws Exception {
        stubReturnHead("REFUND_ONLY", "REQUESTED", "SIMULATED_QR");
        stubReturnDetail("REJECTED");

        ReturnView result = service.decideReturn(1L, 7L, new ReturnDecisionRequest(false, "  Used item  "));

        assertEquals("REJECTED", result.status());
        verify(jdbc).update(contains("status='REJECTED'"), eq("Used item"), eq(1L), eq(7L));
        verify(jdbc).update(contains("INSERT INTO audit_logs"), eq(1L), eq("RETURN_REJECTED"), eq("RETURN_REQUEST"), eq("7"), anyString());
    }

    @Test
    void decideReturn_whenReturnAndRefundApproved_authorizesReturnWithoutRefund() throws Exception {
        stubReturnHead("RETURN_AND_REFUND", "REQUESTED", "SIMULATED_QR");
        stubReturnDetail("RETURN_AUTHORIZED");

        service.decideReturn(1L, 7L, new ReturnDecisionRequest(true, null));

        verify(jdbc).update(contains("status='RETURN_AUTHORIZED'"), eq(null), eq(1L), eq(7L));
        verify(jdbc, never()).update(contains("INSERT INTO refunds"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void decideReturn_whenNothingRefundable_throws409() throws Exception {
        stubReturnHead("RETURN_AND_REFUND", "INSPECTED", "SIMULATED_QR");
        when(jdbc.queryForObject(contains("COALESCE(sum"), eq(BigDecimal.class), any())).thenReturn(BigDecimal.ZERO);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.decideReturn(1L, 7L, new ReturnDecisionRequest(true, null)));

        assertEquals("NO_REFUNDABLE_ITEMS", error.code());
    }

    @Test
    void decideReturn_whenCodRefundOnlyApproved_createsManualCodRefund() throws Exception {
        stubReturnHead("REFUND_ONLY", "REQUESTED", "COD");
        when(jdbc.queryForObject(contains("COALESCE(sum"), eq(BigDecimal.class), any())).thenReturn(new BigDecimal("150000"));
        stubReturnDetail("REFUND_PENDING");

        ReturnView result = service.decideReturn(1L, 7L, new ReturnDecisionRequest(true, "ok"));

        assertEquals("REFUND_PENDING", result.status());
        verify(jdbc).update(contains("INSERT INTO refunds"), eq(7L), eq(3L), eq("MANUAL_COD"),
                eq(new BigDecimal("150000")), eq("RETURN-REQ-7"), eq(1L));
        verify(jdbc).update(contains("status='REFUND_PENDING'"), eq("ok"), eq(1L), eq(7L));
    }

    // ---------- inspectReturn (UC-WH-002) ----------

    @Test
    void inspectReturn_whenNotAuthorized_throws409() throws Exception {
        stubReturnHead("RETURN_AND_REFUND", "REQUESTED", "SIMULATED_QR");

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.inspectReturn(2L, 7L, inspection(5L, 1, 0)));

        assertEquals("RETURN_NOT_AUTHORIZED", error.code());
    }

    @Test
    void inspectReturn_whenNotAllItemsInspected_throws400() throws Exception {
        stubReturnHead("RETURN_AND_REFUND", "RETURN_AUTHORIZED", "SIMULATED_QR");
        when(jdbc.queryForList(contains("FROM return_request_items WHERE return_request_id"), eq(Long.class), any()))
                .thenReturn(List.of(5L, 6L));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.inspectReturn(2L, 7L, inspection(5L, 1, 0)));

        assertEquals("INSPECTION_ITEMS_MISMATCH", error.code());
    }

    @Test
    void inspectReturn_whenReceivedMoreThanRequested_throws400WithoutSaving() throws Exception {
        stubInspectableItem(1);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.inspectReturn(2L, 7L, inspection(5L, 2, 0)));

        assertEquals("INVALID_INSPECTION_QUANTITY", error.code());
        verify(jdbc, never()).update(contains("INSERT INTO return_inspections"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void inspectReturn_whenRestockMoreThanReceived_throws400() throws Exception {
        stubInspectableItem(2);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.inspectReturn(2L, 7L, inspection(5L, 1, 2)));

        assertEquals("INVALID_INSPECTION_QUANTITY", error.code());
    }

    @Test
    void inspectReturn_whenItemReceived_marksEligibleAndInspected() throws Exception {
        stubInspectableItem(1);
        stubReturnDetail("INSPECTED");

        service.inspectReturn(2L, 7L, inspection(5L, 1, 0));

        verify(jdbc).update(contains("INSERT INTO return_inspections"), eq(5L), eq(2L), eq(1), eq("Unused"), eq(0), eq("ELIGIBLE"));
        verify(jdbc).update(contains("status='INSPECTED'"), eq(7L));
        verify(jdbc, never()).queryForObject(contains("UPDATE products"), ArgumentMatchers.<RowMapper<Integer>>any(), any(), any());
    }

    @Test
    void inspectReturn_whenNothingReceived_marksNotEligible() throws Exception {
        stubInspectableItem(1);
        stubReturnDetail("INSPECTED");

        service.inspectReturn(2L, 7L, inspection(5L, 0, 0));

        verify(jdbc).update(contains("INSERT INTO return_inspections"), eq(5L), eq(2L), eq(0), eq("Unused"), eq(0), eq("NOT_ELIGIBLE"));
    }

    @Test
    void inspectReturn_whenRestocked_increasesStockAndLogsMovement() throws Exception {
        stubInspectableItem(2);
        stubReturnDetail("INSPECTED");
        when(jdbc.queryForObject(contains("SELECT product_id"), eq(Long.class), any())).thenReturn(40L);
        when(jdbc.queryForObject(contains("UPDATE products"), ArgumentMatchers.<RowMapper<Integer>>any(), any(), any()))
                .thenReturn(12);

        service.inspectReturn(2L, 7L, inspection(5L, 2, 1));

        verify(jdbc).queryForObject(contains("UPDATE products"), ArgumentMatchers.<RowMapper<Integer>>any(), eq(1), eq(40L));
        verify(jdbc).update(contains("INSERT INTO inventory_movements"), eq(40L), eq(1), eq(12), eq(2L), eq(5L));
    }

    // ---------- refunds (UC-MNG-004) ----------

    @Test
    void completeRefund_whenNotPending_throws409() {
        AuthApiException error = assertThrows(AuthApiException.class, () -> service.completeRefund(2L, 9L));

        assertEquals("REFUND_NOT_PENDING", error.code());
    }

    @Test
    void completeRefund_whenPending_marksRefundAndRequestRefunded() {
        when(jdbc.update(contains("UPDATE refunds"), (Object) any(), (Object) any())).thenReturn(1);
        when(jdbc.queryForObject(contains("WHERE r.id=?"), ArgumentMatchers.<RowMapper<RefundView>>any(), any()))
                .thenReturn(refund("COMPLETED", "CUSTOMER_RETURN"));

        RefundView result = service.completeRefund(2L, 9L);

        assertEquals("COMPLETED", result.status());
        verify(jdbc).update(argThat((String sql) -> sql.contains("status='REFUNDED'") && !sql.contains("PARTIALLY")), eq(9L));
        verify(jdbc).update(contains("INSERT INTO audit_logs"), eq(2L), eq("REFUND_COMPLETED"), eq("REFUND"), eq("9"), anyString());
    }

    @Test
    void createFailedDeliveryRefund_whenOrderMissing_throws404() {
        AuthApiException error = assertThrows(AuthApiException.class, () -> service.createFailedDeliveryRefund(1L, 99L));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
    }

    @Test
    void createFailedDeliveryRefund_whenNotReturnedToWarehouse_throws409() throws Exception {
        stubOrderForRefund("DELIVERED", "SIMULATED_QR");

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.createFailedDeliveryRefund(1L, 99L));

        assertEquals("ORDER_NOT_RETURNED", error.code());
    }

    @Test
    void createFailedDeliveryRefund_whenCodOrder_throws409() throws Exception {
        stubOrderForRefund("RETURNED_TO_WAREHOUSE", "COD");

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.createFailedDeliveryRefund(1L, 99L));

        assertEquals("ORDER_NOT_PREPAID", error.code());
    }

    @Test
    void createFailedDeliveryRefund_whenQrNotPaid_throws409() throws Exception {
        stubOrderForRefund("RETURNED_TO_WAREHOUSE", "SIMULATED_QR");
        when(jdbc.queryForObject(contains("payment_attempts"), eq(Integer.class), any())).thenReturn(0);

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.createFailedDeliveryRefund(1L, 99L));

        assertEquals("ORDER_NOT_PAID", error.code());
    }

    @Test
    void createFailedDeliveryRefund_whenRefundAlreadyExists_throws409() throws Exception {
        stubOrderForRefund("RETURNED_TO_WAREHOUSE", "SIMULATED_QR");
        when(jdbc.queryForObject(contains("payment_attempts"), eq(Integer.class), any())).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM refunds WHERE order_id"), eq(Integer.class), any())).thenReturn(1);

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.createFailedDeliveryRefund(1L, 99L));

        assertEquals("REFUND_ALREADY_EXISTS", error.code());
        verify(jdbc, never()).queryForObject(contains("INSERT INTO refunds"), eq(Long.class), any(), any(), any(), any());
    }

    @Test
    void createFailedDeliveryRefund_whenValid_createsPendingRefundForOrderTotal() throws Exception {
        stubOrderForRefund("RETURNED_TO_WAREHOUSE", "SIMULATED_QR");
        when(jdbc.queryForObject(contains("payment_attempts"), eq(Integer.class), any())).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM refunds WHERE order_id"), eq(Integer.class), any())).thenReturn(0);
        when(jdbc.queryForObject(contains("INSERT INTO refunds"), eq(Long.class), any(), any(), any(), any())).thenReturn(55L);
        when(jdbc.queryForObject(contains("WHERE r.id=?"), ArgumentMatchers.<RowMapper<RefundView>>any(), any()))
                .thenReturn(refund("PENDING", "FAILED_PREPAID_DELIVERY"));

        RefundView result = service.createFailedDeliveryRefund(1L, 99L);

        assertEquals("PENDING", result.status());
        assertEquals("FAILED_PREPAID_DELIVERY", result.reason());
        verify(jdbc).queryForObject(contains("INSERT INTO refunds"), eq(Long.class), eq(99L),
                eq(new BigDecimal("250000")), eq("FAILED-DELIVERY-99"), eq(1L));
        verify(jdbc).update(contains("INSERT INTO audit_logs"), eq(1L), eq("REFUND_CREATED"), eq("REFUND"), eq("55"), anyString());
    }

    // ---------- helpers ----------

    private static CreateReturnRequest returnRequest(String type, int quantity) {
        return new CreateReturnRequest(type, "Wrong size", null, List.of(new ReturnItemRequest(10L, quantity)));
    }

    private static InspectionRequest inspection(long itemId, int received, int restock) {
        return new InspectionRequest(List.of(new ItemInspection(itemId, received, "Unused", restock)));
    }

    private static RefundView refund(String status, String reason) {
        return new RefundView(9L, 99L, null, "ORD-99", reason, "SIMULATED_QR", status,
                new BigDecimal("250000"), OffsetDateTime.now(), null);
    }

    /** Lets the service's own row mapper read a mocked row, so private row records stay private. */
    private void answerWithRow(String sqlPart, ResultSet row, int argumentCount) {
        if (argumentCount == 1) {
            when(jdbc.query(contains(sqlPart), ArgumentMatchers.<RowMapper<Object>>any(), any()))
                    .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(row, 0)));
        } else {
            when(jdbc.query(contains(sqlPart), ArgumentMatchers.<RowMapper<Object>>any(), any(), any()))
                    .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(row, 0)));
        }
    }

    private void stubCustomerOrder(String status, OffsetDateTime deliveredAt) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn(status);
        when(row.getString(2)).thenReturn("SIMULATED_QR");
        when(row.getObject(3, OffsetDateTime.class)).thenReturn(deliveredAt);
        answerWithRow("FROM orders WHERE id=? AND customer_id=?", row, 2);
    }

    private void stubReturnHead(String type, String status, String paymentMethod) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(3L);
        when(row.getString(2)).thenReturn(type);
        when(row.getString(3)).thenReturn(status);
        when(row.getString(4)).thenReturn(paymentMethod);
        answerWithRow("FOR UPDATE OF rr", row, 1);
    }

    private void stubReturnDetail(String status) {
        ReturnView view = new ReturnView(7L, 3L, "ORD-3", 5L, "Lan", "REFUND_ONLY", status, "Wrong size",
                null, null, OffsetDateTime.now(), List.of());
        when(jdbc.queryForObject(contains("FROM return_requests rr JOIN orders o"), ArgumentMatchers.<RowMapper<ReturnView>>any(), any()))
                .thenReturn(view);
    }

    private void stubInspectableItem(int requestedQuantity) throws Exception {
        stubReturnHead("RETURN_AND_REFUND", "RETURN_AUTHORIZED", "SIMULATED_QR");
        when(jdbc.queryForList(contains("FROM return_request_items WHERE return_request_id"), eq(Long.class), any()))
                .thenReturn(List.of(5L));
        when(jdbc.queryForObject(contains("SELECT quantity FROM return_request_items"), eq(Integer.class), any(), any()))
                .thenReturn(requestedQuantity);
    }

    private void stubOrderForRefund(String status, String paymentMethod) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn(status);
        when(row.getString(2)).thenReturn(paymentMethod);
        when(row.getBigDecimal(3)).thenReturn(new BigDecimal("250000"));
        answerWithRow("FROM orders WHERE id=? FOR UPDATE", row, 1);
    }
}
