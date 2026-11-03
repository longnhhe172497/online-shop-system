package com.osmshop.auth;

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

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Pure service tests for email-change guards. */
class EmailChangeServiceUnitTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final AuthRateLimiter limits = mock(AuthRateLimiter.class);
    private final EmailChangeService service = new EmailChangeService(jdbc, passwords, mail,
            transactions, limits, "sender@example.com", "http://localhost:5173/");

    @Test
    void request_whenPasswordWrong_rejectsBeforeCheckingNewEmail() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("stored-hash");
        when(passwords.matches("wrong", "stored-hash")).thenReturn(false);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.request(7L, " NEW@EXAMPLE.COM ", "wrong"));

        assertEquals("INVALID_CURRENT_PASSWORD", error.code());
        verify(limits).check("email-change-user", "7", 5, 86400);
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), eq("new@example.com"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void request_whenEmailAlreadyRegistered_rejectsBeforeSendingMail() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("stored-hash");
        when(passwords.matches("correct", "stored-hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("new@example.com"))).thenReturn(1L);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.request(7L, " NEW@EXAMPLE.COM ", "correct"));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertEquals("EMAIL_ALREADY_REGISTERED", error.code());
        verify(transactions, never()).executeWithoutResult(any());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void confirm_whenTokenUnknown_rejectsWithoutChangingAccount() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.confirm("unknown-token"));

        assertEquals("INVALID_EMAIL_CHANGE_TOKEN", error.code());
        verify(jdbc, never()).update(anyString(), eq("new@example.com"), eq(7L));
    }

    @Test
    void request_whenValid_sendsVerificationToNormalizedNewEmail() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        runTransactionCallback();

        service.request(7L, " NEW@EXAMPLE.COM ", "correct");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO email_change_requests"),
                eq(7L), eq("new@example.com"), anyString(), any());
        org.mockito.ArgumentCaptor<SimpleMailMessage> message =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertEquals("new@example.com", message.getValue().getTo()[0]);
    }

    @Test
    void request_whenSmtpFails_invalidatesPendingToken() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        runTransactionCallback();
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> service.request(7L, "new@example.com", "correct"));

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET used_at=now() WHERE token_hash="), anyString());
    }

    @Test
    void confirm_validToken_changesEmailAndRevokesPreviousSessions() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(11L);
        when(row.getLong(2)).thenReturn(7L);
        when(row.getString(3)).thenReturn("new@example.com");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), anyString()))
                .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));

        service.confirm("valid-token");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE users SET email="),
                eq("new@example.com"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("DELETE FROM google_identities"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"), eq(7L));
    }

    private void runTransactionCallback() {
        doAnswer(call -> {
            java.util.function.Consumer<TransactionStatus> callback = call.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }
}
