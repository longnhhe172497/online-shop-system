package com.osmshop.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.osmshop.admin.InvitationController.AcceptRequest;
import com.osmshop.admin.InvitationController.InviteRequest;
import com.osmshop.auth.AuthApiException;
import java.util.List;
import java.time.OffsetDateTime;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.dao.DataIntegrityViolationException;

/** Pure tests for staff invitation guards and state transitions. */
class   InvitationServiceUnitTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final InvitationService service = new InvitationService(jdbc, passwords, mail,
            transactions, "sender@example.com", "http://localhost:5173/");

    @Test
    void invite_customerRole_rejectedBeforeTransaction() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.invite(1L, new InviteRequest("new@example.com", "New", null, "CUSTOMER")));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertEquals("INVALID_ROLE", error.code());
        verify(transactions, never()).execute(any());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resend_missingOrIneligibleInvitation_rejected() {
        AuthApiException error = assertThrows(AuthApiException.class, () -> service.resend(1L, 99L));

        assertEquals("INVITATION_NOT_RESENDABLE", error.code());
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(99L));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void revoke_whenAlreadyRevoked_rejectedWithoutAudit() {
        when(jdbc.update(anyString(), eq(99L))).thenReturn(0);

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.revoke(1L, 99L));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertEquals("INVITATION_NOT_REVOCABLE", error.code());
        verify(jdbc, never()).update(anyString(), eq(1L), eq("99"));
    }

    @Test
    void accept_passwordMismatch_rejectedWithoutLookingUpToken() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.accept(new AcceptRequest("token", "password-123", "different")));

        assertEquals("PASSWORD_MISMATCH", error.code());
        verify(jdbc, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any());
    }

    @Test
    void accept_unknownExpiredOrUsedToken_rejectedWithoutCreatingUser() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.accept(new AcceptRequest("invalid", "password-123", "password-123")));

        assertEquals("INVALID_INVITATION", error.code());
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any());
    }

    @Test
    void accept_validInvitation_createsActiveStaffAndMarksAccepted() throws Exception {
        stubPendingInvitation();
        when(passwords.encode("password-123")).thenReturn("hash");
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO users"),
                eq(Long.class), eq("new@example.com"), eq("hash"), eq("New"), eq(null),
                eq("SUPPORT"))).thenReturn(7L);

        service.accept(new AcceptRequest("valid", "password-123", "password-123"));

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("accepted_at=now()"), eq(11L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INVITATION_ACCEPTED"),
                eq(1L), eq("7"));
    }

    @Test
    void accept_whenEmailWasRegisteredConcurrently_returnsConflict() throws Exception {
        stubPendingInvitation();
        when(passwords.encode("password-123")).thenReturn("hash");
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO users"),
                eq(Long.class), eq("new@example.com"), eq("hash"), eq("New"), eq(null),
                eq("SUPPORT"))).thenThrow(new DataIntegrityViolationException("duplicate"));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.accept(new AcceptRequest("valid", "password-123", "password-123")));

        assertEquals("EMAIL_ALREADY_REGISTERED", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("accepted_at=now()"), eq(11L));
    }

    @Test
    void list_whenNoInvitations_returnsEmpty() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<InvitationController.InvitationView>>any()))
                .thenReturn(List.of());

        assertEquals(List.of(), service.list());
    }

    @Test
    void invite_validStaff_sendsMailAndRecordsAudit() {
        runInvitationTransaction();
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO staff_invitations"),
                eq(Long.class), eq("new@example.com"), eq("New"), eq(null), eq("SUPPORT"),
                anyString(), eq(1L), any())).thenReturn(11L);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("delivery_status='SENT'"), eq(11L)))
                .thenReturn(1);
        var expected = new InvitationController.InvitationView(11L, "new@example.com", "New", "SUPPORT",
                OffsetDateTime.now().plusDays(2), null, OffsetDateTime.now(), "SENT");
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM staff_invitations WHERE id=?"),
                org.mockito.ArgumentMatchers.<RowMapper<InvitationController.InvitationView>>any(), eq(11L)))
                .thenReturn(expected);

        var result = service.invite(1L, new InviteRequest(" NEW@EXAMPLE.COM ", " New ", null, "support"));

        assertEquals(expected, result);
        org.mockito.ArgumentCaptor<SimpleMailMessage> message =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertEquals("new@example.com", message.getValue().getTo()[0]);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("USER_INVITED"),
                eq(1L), eq("new@example.com"), eq("SUPPORT"));
    }

    @Test
    void invite_whenSmtpFails_marksInvitationFailed() {
        runInvitationTransaction();
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO staff_invitations"),
                eq(Long.class), eq("new@example.com"), eq("New"), eq(null), eq("SUPPORT"),
                anyString(), eq(1L), any())).thenReturn(11L);
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.invite(1L, new InviteRequest("new@example.com", "New", null, "SUPPORT")));

        assertEquals("INVITATION_DELIVERY_FAILED", error.code());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("delivery_status='FAILED'"), eq(11L));
    }

    private void runInvitationTransaction() {
        doAnswer(call -> {
            TransactionCallback<?> callback = call.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactions).execute(any());
        doAnswer(call -> {
            java.util.function.Consumer<TransactionStatus> callback = call.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    private void stubPendingInvitation() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(11L);
        when(row.getString(2)).thenReturn("new@example.com");
        when(row.getString(3)).thenReturn("New");
        when(row.getString(4)).thenReturn(null);
        when(row.getString(5)).thenReturn("SUPPORT");
        when(row.getLong(6)).thenReturn(1L);
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("FROM staff_invitations"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), anyString()))
                .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
    }
}
