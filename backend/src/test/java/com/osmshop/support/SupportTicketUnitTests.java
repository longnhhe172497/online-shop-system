package com.osmshop.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.osmshop.auth.AuthApiException;
import com.osmshop.support.AfterSalesDtos.CreateTicketRequest;
import com.osmshop.support.AfterSalesDtos.TicketView;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Pure service tests for support tickets in AfterSalesService (UC-CUS-019, UC-SUP-002):
 * no Spring context, HTTP layer or database.
 */
class SupportTicketUnitTests {
    private static final long CUSTOMER = 5L;
    private static final long STAFF = 8L;
    private static final long TICKET = 3L;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AfterSalesService service = new AfterSalesService(jdbc);

    @Test
    void createTicket_whenLinkedOrderNotOwned_throws404WithoutCreatingTicket() {
        when(jdbc.queryForObject(contains("FROM orders WHERE id=? AND customer_id=?"), eq(Integer.class), any(), any()))
                .thenReturn(0);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.createTicket(CUSTOMER, new CreateTicketRequest("Late order", 77L, "Where is it?")));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        verify(jdbc, never()).queryForObject(contains("INSERT INTO support_tickets"), eq(Long.class), any(), any(), any());
    }

    @Test
    void createTicket_whenValid_createsOpenTicketWithFirstMessage() throws Exception {
        when(jdbc.queryForObject(contains("INSERT INTO support_tickets"), eq(Long.class), any(), any(), any()))
                .thenReturn(TICKET);
        stubTicket(CUSTOMER, "OPEN");

        TicketView result = service.createTicket(CUSTOMER, new CreateTicketRequest("  Size question  ", null, "  Is M fit?  "));

        assertEquals("OPEN", result.status());
        verify(jdbc).queryForObject(contains("INSERT INTO support_tickets"), eq(Long.class), eq(CUSTOMER), eq(null), eq("Size question"));
        verify(jdbc).update(contains("INSERT INTO ticket_messages"), eq(TICKET), eq(CUSTOMER), eq("Is M fit?"));
        verify(jdbc).update(contains("INSERT INTO audit_logs"), eq(CUSTOMER), eq("SUPPORT_TICKET_CREATED"), eq("SUPPORT_TICKET"), eq("3"), anyString());
    }

    @Test
    void customerReplyTicket_whenTicketBelongsToAnotherCustomer_throws404() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.customerReplyTicket(CUSTOMER, TICKET, "Hello"));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        verify(jdbc, never()).update(contains("INSERT INTO ticket_messages"), any(), any(), any());
    }

    @Test
    void customerReplyTicket_whenClosed_throws409() throws Exception {
        stubTicket(CUSTOMER, "CLOSED");

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.customerReplyTicket(CUSTOMER, TICKET, "Hello again"));

        assertEquals("TICKET_CLOSED", error.code());
        verify(jdbc, never()).update(contains("INSERT INTO ticket_messages"), any(), any(), any());
    }

    @Test
    void customerReplyTicket_whenOpen_savesMessage() throws Exception {
        stubTicket(CUSTOMER, "IN_PROGRESS");

        service.customerReplyTicket(CUSTOMER, TICKET, "  Thanks  ");

        verify(jdbc).update(contains("INSERT INTO ticket_messages"), eq(TICKET), eq(CUSTOMER), eq("Thanks"));
    }

    @Test
    void replyTicket_whenTicketMissing_throws404() {
        AuthApiException error = assertThrows(AuthApiException.class, () -> service.replyTicket(STAFF, TICKET, "Hi"));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
    }

    @Test
    void replyTicket_whenClosed_throws409() {
        stubStatus("CLOSED");

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.replyTicket(STAFF, TICKET, "Hi"));

        assertEquals("TICKET_CLOSED", error.code());
        verify(jdbc, never()).update(contains("INSERT INTO ticket_messages"), any(), any(), any());
    }

    @Test
    void replyTicket_whenOpen_savesReplyAssignsStaffAndMovesToInProgress() throws Exception {
        stubStatus("OPEN");
        stubTicket(CUSTOMER, "IN_PROGRESS");

        TicketView result = service.replyTicket(STAFF, TICKET, "  We are checking  ");

        assertEquals("IN_PROGRESS", result.status());
        verify(jdbc).update(contains("INSERT INTO ticket_messages"), eq(TICKET), eq(STAFF), eq("We are checking"));
        verify(jdbc).update(contains("assigned_staff_id=COALESCE(assigned_staff_id,?)"), eq(STAFF), eq(TICKET));
    }

    @Test
    void updateTicketStatus_whenClosed_throws409WithoutUpdate() {
        stubStatus("CLOSED");

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.updateTicketStatus(STAFF, TICKET, "RESOLVED"));

        assertEquals("TICKET_CLOSED", error.code());
        verify(jdbc, never()).update(contains("SET status=?"), any(), any(), any());
    }

    @Test
    void updateTicketStatus_whenResolved_updatesStatusAndAudits() throws Exception {
        stubStatus("IN_PROGRESS");
        stubTicket(CUSTOMER, "RESOLVED");

        TicketView result = service.updateTicketStatus(STAFF, TICKET, "RESOLVED");

        assertEquals("RESOLVED", result.status());
        verify(jdbc).update(contains("SET status=?"), eq("RESOLVED"), eq(STAFF), eq(TICKET));
        verify(jdbc).update(contains("INSERT INTO audit_logs"), eq(STAFF), eq("SUPPORT_TICKET_STATUS_CHANGED"),
                eq("SUPPORT_TICKET"), eq("3"), eq("{\"status\":\"RESOLVED\"}"));
    }

    // ---------- helpers ----------

    /** Current status read with SELECT ... FOR UPDATE by staff actions. */
    private void stubStatus(String status) {
        when(jdbc.query(contains("SELECT status FROM support_tickets"), ArgumentMatchers.<RowMapper<String>>any(), any()))
                .thenReturn(List.of(status));
    }

    /** Ticket header row read by the service's own row mapper (customer lookups and ticket detail). */
    private void stubTicket(long customerId, String status) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(TICKET);
        when(row.getLong(2)).thenReturn(customerId);
        when(row.getString(3)).thenReturn("Lan");
        when(row.getString(6)).thenReturn("Size question");
        when(row.getString(7)).thenReturn(status);
        when(row.getObject(9, OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
        when(row.getObject(10, OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
        when(jdbc.query(contains("FROM support_tickets t JOIN users u"), ArgumentMatchers.<RowMapper<Object>>any(), any()))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(row, 0)));
        when(jdbc.query(contains("FROM support_tickets t JOIN users u"), ArgumentMatchers.<RowMapper<Object>>any(), any(), any()))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(row, 0)));
    }
}
