package com.osmshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.osmshop.auth.GoogleIdTokenVerifier.GoogleIdentity;
import java.util.List;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

/** Pure tests: Google's token verifier and the database are mocked. */
class GoogleIdentityServiceUnitTests {
    private final GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AuthRateLimiter limits = mock(AuthRateLimiter.class);
    private final AuthService auth = mock(AuthService.class);
    private final GoogleIdentityService service = new GoogleIdentityService(verifier, jdbc, passwords, limits, auth);

    @Test
    void enabledAndClientId_reflectVerifierConfiguration() {
        when(verifier.enabled()).thenReturn(true);
        when(verifier.clientId()).thenReturn("client-id");

        assertTrue(service.enabled());
        assertEquals("client-id", service.clientId());
    }

    @Test
    void isLinked_whenNoRow_returnsFalse() {
        assertFalse(service.isLinked(7L));
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq(7L));
    }

    @Test
    void isLinked_whenLinked_returnsTrue() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);

        assertTrue(service.isLinked(7L));
    }

    @Test
    void login_whenExistingGoogleSubject_reusesAccount() {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "long@example.com", "Long"));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("subject")))
                .thenReturn(List.of(7L));

        service.login("credential", "127.0.0.1", "agent");

        verify(limits).check("google-login-ip", "127.0.0.1", 100, 3600);
        verify(auth).loginGoogleUser(7L, "127.0.0.1", "agent");
        verify(jdbc, never()).update(anyString(), eq(7L), eq("subject"));
    }

    @Test
    void login_whenEmailAlreadyExists_requiresExplicitLink() {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "long@example.com", "Long"));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("long@example.com")))
                .thenReturn(List.of(7L));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.login("credential", "127.0.0.1", "agent"));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertEquals("GOOGLE_LINK_REQUIRED", error.code());
        verify(auth, never()).loginGoogleUser(eq(7L), anyString(), anyString());
    }

    @Test
    void login_whenNewGoogleEmail_createsCustomerAndLinksSubject() {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "new@example.com", "New"));
        when(passwords.encode(anyString())).thenReturn("random-hash");
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("INSERT INTO users"),
                org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("new@example.com"),
                eq("random-hash"), eq("New"))).thenReturn(List.of(7L));

        service.login("credential", "127.0.0.1", "agent");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO google_identities"),
                eq(7L), eq("subject"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("AUTH_GOOGLE_REGISTER"), eq(7L), eq("7"));
        verify(auth).loginGoogleUser(7L, "127.0.0.1", "agent");
    }

    @Test
    void login_whenConcurrentRequestLinkedSameSubject_reusesLinkedUser() {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "new@example.com", "New"));
        when(passwords.encode(anyString())).thenReturn("random-hash");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("subject")))
                .thenReturn(List.of(), List.of(7L));

        service.login("credential", "127.0.0.1", "agent");

        verify(auth).loginGoogleUser(7L, "127.0.0.1", "agent");
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO google_identities"),
                eq(7L), eq("subject"));
    }

    @Test
    void login_whenConcurrentEmailHasNoMatchingGoogleLink_requiresLink() {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "new@example.com", "New"));
        when(passwords.encode(anyString())).thenReturn("random-hash");

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.login("credential", "127.0.0.1", "agent"));

        assertEquals("GOOGLE_LINK_REQUIRED", error.code());
    }

    @Test
    void link_whenAccountUnavailable_rejectsBeforeWritingIdentity() {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "long@example.com", "Long"));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.link(7L, "password", "credential"));

        assertEquals("INVALID_CURRENT_PASSWORD", error.code());
        verify(jdbc, never()).update(anyString(), eq(7L), eq("subject"));
    }

    @Test
    void link_whenGoogleEmailDiffers_rejectsBeforeWritingIdentity() throws Exception {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "other@example.com", "Other"));
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn("long@example.com");
        when(row.getString(2)).thenReturn("hash");
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("SELECT email,password_hash"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L)))
                .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
        when(passwords.matches("correct", "hash")).thenReturn(true);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.link(7L, "correct", "credential"));

        assertEquals("GOOGLE_EMAIL_MISMATCH", error.code());
        verify(jdbc, never()).update(anyString(), eq(7L), eq("subject"));
    }

    @Test
    void link_whenSubjectAlreadyLinked_returnsConflict() throws Exception {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "long@example.com", "Long"));
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn("long@example.com");
        when(row.getString(2)).thenReturn("hash");
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("SELECT email,password_hash"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L)))
                .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("INSERT INTO google_identities"),
                eq(7L), eq("subject"))).thenThrow(new DataIntegrityViolationException("duplicate"));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.link(7L, "correct", "credential"));

        assertEquals("GOOGLE_ALREADY_LINKED", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("AUTH_GOOGLE_LINK"),
                eq(7L), eq("7"));
    }

    @Test
    void link_whenEmailMatches_savesIdentityAndAudit() throws Exception {
        when(verifier.verify("credential")).thenReturn(new GoogleIdentity("subject", "long@example.com", "Long"));
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn("long@example.com");
        when(row.getString(2)).thenReturn("hash");
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("SELECT email,password_hash"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L)))
                .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
        when(passwords.matches("correct", "hash")).thenReturn(true);

        service.link(7L, "correct", "credential");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO google_identities"),
                eq(7L), eq("subject"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("AUTH_GOOGLE_LINK"), eq(7L), eq("7"));
    }

    @Test
    void unlink_whenPasswordWrong_doesNotDeleteIdentity() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("wrong", "hash")).thenReturn(false);

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.unlink(7L, "wrong"));

        assertEquals("INVALID_CURRENT_PASSWORD", error.code());
        verify(jdbc, never()).update(anyString(), eq(7L));
    }

    @Test
    void unlink_whenNotLinked_rejectsAfterOwnerCheck() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.update(anyString(), eq(7L))).thenReturn(0);

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.unlink(7L, "correct"));

        assertEquals("GOOGLE_NOT_LINKED", error.code());
        verify(jdbc).update(anyString(), eq(7L));
        verify(jdbc, never()).update(anyString(), eq(7L), eq("7"));
    }

    @Test
    void unlink_whenLinked_removesIdentityAndAudits() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("DELETE FROM google_identities"), eq(7L)))
                .thenReturn(1);

        service.unlink(7L, "correct");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("AUTH_GOOGLE_UNLINK"), eq(7L), eq("7"));
    }
}
