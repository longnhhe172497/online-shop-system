package com.osmshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.osmshop.auth.AuthDtos.LoginRequest;
import com.osmshop.auth.AuthDtos.PasswordResetConfirmRequest;
import com.osmshop.auth.AuthDtos.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

/** Pure service tests: no application context, database, SMTP or HTTP server. */
class AuthServiceUnitTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final AuthRateLimiter limits = mock(AuthRateLimiter.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final TotpService totp = mock(TotpService.class);
    private final AuthService service = new AuthService(jdbc, passwords, mail, limits, transactions,
            totp, "sender@example.com", "http://localhost:5173/");

    @Test
    void register_whenConfirmationDiffers_rejectsWithoutSendingMail() {
        var request = new RegisterRequest("Long", "long@example.com", "password-123", "other-123", null);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.register(request, "127.0.0.1"));

        assertEquals("PASSWORD_MISMATCH", error.code());
        verify(limits).check("register", "127.0.0.1", 30, 3600);
        verify(mail, never()).send(any(SimpleMailMessage.class));
        verify(transactions, never()).execute(any());
    }

    @Test
    void register_validRequest_createsPendingCustomerAndSendsMail() {
        runTransactionCallback();
        when(passwords.encode("password-123")).thenReturn("hash");
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO users"), eq(Long.class),
                eq("long@example.com"), eq("hash"), eq("Long"), eq(null))).thenReturn(7L);

        var result = service.register(new RegisterRequest(" Long ", " LONG@EXAMPLE.COM ",
                "password-123", "password-123", null), "127.0.0.1");

        assertEquals(7L, result.userId());
        assertEquals(true, result.verificationRequired());
        assertEquals(true, result.emailSent());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO auth_tokens"),
                eq(7L), anyString(), any());
        org.mockito.ArgumentCaptor<SimpleMailMessage> message =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertEquals("long@example.com", message.getValue().getTo()[0]);
    }

    @Test
    void register_whenSmtpFails_reportsEmailNotSentAndInvalidatesToken() {
        runTransactionCallback();
        when(passwords.encode("password-123")).thenReturn("hash");
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO users"), eq(Long.class),
                eq("long@example.com"), eq("hash"), eq("Long"), eq(null))).thenReturn(7L);
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));

        var result = service.register(new RegisterRequest("Long", "long@example.com",
                "password-123", "password-123", null), "127.0.0.1");

        assertEquals(false, result.emailSent());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET used_at=now() WHERE token_hash="), anyString());
    }

    @Test
    void verify_whenTokenDoesNotExist_rejectsWithoutActivatingUser() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.verify("invalid-token"));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertEquals("INVALID_VERIFICATION_TOKEN", error.code());
        verify(jdbc, never()).update(anyString(), eq(7L));
    }

    @Test
    void verify_whenValidToken_activatesPendingUser() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), anyString()))
                .thenReturn(java.util.List.of(7L));
        when(jdbc.update(anyString(), eq(7L))).thenReturn(1);

        var result = service.verify("valid-token");

        assertEquals(true, result.verified());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status = 'ACTIVE'"), eq(7L));
    }

    @Test
    void verify_whenPendingUserWasChanged_rejectsConsumedToken() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), anyString()))
                .thenReturn(java.util.List.of(7L));
        when(jdbc.update(anyString(), eq(7L))).thenReturn(0);

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.verify("valid-token"));

        assertEquals("INVALID_VERIFICATION_TOKEN", error.code());
    }

    @Test
    void login_whenEmailNotFound_recordsFailureAndRejects() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.login(new LoginRequest("  LONG@EXAMPLE.COM ", "wrong-password"),
                        "127.0.0.1", "test-agent"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        assertEquals("INVALID_CREDENTIALS", error.code());
        verify(limits).check("login-email", "long@example.com", 10, 900);
        verify(limits).check("login-ip", "127.0.0.1", 100, 900);
        verify(jdbc).update(anyString(), eq(null), eq("127.0.0.1"));
    }

    @Test
    void login_whenVerifiedAndMfaDisabled_createsBearerSession() throws Exception {
        stubStoredUser("long@example.com");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);

        var result = service.login(new LoginRequest(" LONG@EXAMPLE.COM ", "correct"),
                "127.0.0.1", "agent");

        assertEquals(7L, result.user().id());
        assertEquals(false, result.mfaRequired());
        assertEquals(true, !result.accessToken().isBlank());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO user_sessions"),
                eq(7L), anyString(), any(), eq("127.0.0.1"), eq("agent"));
    }

    @Test
    void login_whenEmailMfaEnabled_returnsChallengeWithoutSession() throws Exception {
        stubStoredUser("long@example.com");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("EMAIL");

        var result = service.login(new LoginRequest("long@example.com", "correct"),
                "127.0.0.1", "agent");

        assertEquals(true, result.mfaRequired());
        assertEquals("EMAIL", result.mfaMethod());
        verify(mail).send(any(SimpleMailMessage.class));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO user_sessions"),
                eq(7L), anyString(), any(), anyString(), anyString());
    }

    @Test
    void login_whenPasswordWrong_recordsKnownAccountFailure() throws Exception {
        stubStoredUser("long@example.com");
        when(passwords.matches("wrong", "hash")).thenReturn(false);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.login(new LoginRequest("long@example.com", "wrong"),
                        "127.0.0.1", "agent"));

        assertEquals("INVALID_CREDENTIALS", error.code());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("AUTH_LOGIN_FAILED"),
                eq("7"), eq("127.0.0.1"));
    }

    @Test
    void login_whenAccountInactive_rejectsDespiteCorrectPassword() throws Exception {
        stubStoredUser("long@example.com", "LOCKED", true);
        when(passwords.matches("correct", "hash")).thenReturn(true);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.login(new LoginRequest("long@example.com", "correct"),
                        "127.0.0.1", "agent"));

        assertEquals("INVALID_CREDENTIALS", error.code());
    }

    @Test
    void login_whenEmailUnverified_rejectsDespiteCorrectPassword() throws Exception {
        stubStoredUser("long@example.com", "ACTIVE", false);
        when(passwords.matches("correct", "hash")).thenReturn(true);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.login(new LoginRequest("long@example.com", "correct"),
                        "127.0.0.1", "agent"));

        assertEquals("INVALID_CREDENTIALS", error.code());
    }

    @Test
    void loginGoogleUser_whenAccountMissing_rejects() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.loginGoogleUser(99L, "127.0.0.1", "agent"));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        assertEquals("ACCOUNT_UNAVAILABLE", error.code());
    }

    @Test
    void loginGoogleUser_whenTOTPEnabled_returnsAuthenticatorChallengeWithoutMail() throws Exception {
        stubStoredUserById();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("TOTP");

        var result = service.loginGoogleUser(7L, "127.0.0.1", "agent");

        assertEquals(true, result.mfaRequired());
        assertEquals("TOTP", result.mfaMethod());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void changePassword_whenConfirmationDiffers_doesNotReadOrWritePassword() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changePassword(7L, "old-password", "new-password", "different"));

        assertEquals("PASSWORD_MISMATCH", error.code());
        verify(jdbc, never()).queryForObject(anyString(), eq(String.class), eq(7L));
    }

    @Test
    void changePassword_whenCurrentPasswordWrong_doesNotRevokeSessions() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("stored-hash");
        when(passwords.matches("wrong", "stored-hash")).thenReturn(false);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changePassword(7L, "wrong", "new-password", "new-password"));

        assertEquals("INVALID_CURRENT_PASSWORD", error.code());
        verify(passwords).matches("wrong", "stored-hash");
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    @Test
    void changePassword_whenValid_replacesHashAndRevokesAllSessions() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("old-hash");
        when(passwords.matches("correct", "old-hash")).thenReturn(true);
        when(passwords.encode("new-password")).thenReturn("new-hash");

        service.changePassword(7L, "correct", "new-password", "new-password");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE users SET password_hash"),
                eq("new-hash"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE email_change_requests"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("PASSWORD_CHANGED"), eq(7L), eq("7"));
    }

    @Test
    void requestPasswordReset_whenEmailUnknown_doesNotSendMail() {
        service.requestPasswordReset(" UNKNOWN@EXAMPLE.COM ", "127.0.0.1");

        verify(limits).check("reset-email", "unknown@example.com", 5, 3600);
        verify(mail, never()).send(any(SimpleMailMessage.class));
        verify(transactions, never()).execute(any());
    }

    @Test
    void requestPasswordReset_whenRecentTokenExists_doesNotSendAnotherMail() {
        runTransactionCallback();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("long@example.com")))
                .thenReturn(java.util.List.of(7L));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(7L, 1L);

        service.requestPasswordReset("long@example.com", "127.0.0.1");

        verify(mail, never()).send(any(SimpleMailMessage.class));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO auth_tokens"),
                eq(7L), anyString(), any());
    }

    @Test
    void requestPasswordReset_whenEligible_sendsResetLink() {
        runTransactionCallback();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("long@example.com")))
                .thenReturn(java.util.List.of(7L));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(7L, 0L);

        service.requestPasswordReset("long@example.com", "127.0.0.1");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO auth_tokens"),
                eq(7L), anyString(), any());
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void confirmPasswordReset_whenConfirmationDiffers_rejectsBeforeTokenLookup() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.confirmPasswordReset(new PasswordResetConfirmRequest(
                        "token", "new-password", "different")));

        assertEquals("PASSWORD_MISMATCH", error.code());
        verify(jdbc, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), anyString());
    }

    @Test
    void confirmPasswordReset_whenTokenInvalid_doesNotChangePassword() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.confirmPasswordReset(new PasswordResetConfirmRequest(
                        "invalid", "new-password", "new-password")));

        assertEquals("INVALID_RESET_TOKEN", error.code());
        verify(passwords, never()).encode(anyString());
    }

    @Test
    void confirmPasswordReset_whenTokenValid_changesPasswordAndRevokesSessions() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), anyString()))
                .thenReturn(java.util.List.of(7L));
        when(passwords.encode("new-password")).thenReturn("new-hash");

        var result = service.confirmPasswordReset(new PasswordResetConfirmRequest(
                "valid", "new-password", "new-password"));

        assertEquals(true, result.reset());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET password_hash ="),
                eq("new-hash"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE email_change_requests"), eq(7L));
    }

    @Test
    void resendVerification_whenAccountNotPending_doesNotSendMail() {
        service.resendVerification(" LONG@EXAMPLE.COM ", "127.0.0.1");

        verify(limits).check("verification-email", "long@example.com", 5, 3600);
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendVerification_whenRecentTokenExists_skipsSendingAgain() {
        runTransactionCallback();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("long@example.com")))
                .thenReturn(java.util.List.of(7L));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(7L, 1L);

        service.resendVerification("long@example.com", "127.0.0.1");

        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendVerification_whenEligible_sendsNewLink() {
        runTransactionCallback();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("long@example.com")))
                .thenReturn(java.util.List.of(7L));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(7L, 0L);

        service.resendVerification("long@example.com", "127.0.0.1");

        verify(mail).send(any(SimpleMailMessage.class));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO auth_tokens"),
                eq(7L), anyString(), any());
    }

    @Test
    void logout_revokesOnlyPresentedTokenForOwner() {
        service.logout(7L, "raw-token");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"),
                eq(7L), eq(AuthService.hash("raw-token")));
    }

    @Test
    void startMfaEnrollment_whenAlreadyEnabled_rejectsBeforeSendingCode() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(true);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.startMfaEnrollment(7L));

        assertEquals("MFA_ALREADY_ENABLED", error.code());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void startMfaEnrollment_whenDisabled_sendsEmailCode() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("long@example.com");

        String challenge = service.startMfaEnrollment(7L);

        assertEquals(true, !challenge.isBlank());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO email_mfa_challenges"),
                eq(7L), anyString(), anyString(), eq("ENROLL"), any());
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void startMfaEnrollment_whenSmtpFails_propagatesFailure() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("long@example.com");
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> service.startMfaEnrollment(7L));
    }

    @Test
    void finishMfaEnrollment_whenAlreadyEnabled_rejectsBeforeChallengeLookup() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(true);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.finishMfaEnrollment(7L, "challenge", "123456"));

        assertEquals("MFA_ALREADY_ENABLED", error.code());
    }

    @Test
    void finishMfaEnrollment_whenChallengeExpired_rejects() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.finishMfaEnrollment(7L, "expired", "123456"));

        assertEquals("INVALID_MFA_CHALLENGE", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("mfa_enabled=true"), eq(7L));
    }

    @Test
    void finishMfaEnrollment_whenEmailCodeCorrect_enablesMfaAndIssuesEightRecoveryCodes() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);
        stubEmailMfaChallenge("challenge", "ENROLL", 7L,
                AuthService.hash("challenge:123456"), 0);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("long@example.com");

        var codes = service.finishMfaEnrollment(7L, "challenge", "123456");

        assertEquals(8, codes.size());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("mfa_enabled=true"), eq(7L));
        verify(jdbc, org.mockito.Mockito.times(8)).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO mfa_recovery_codes"), eq(7L), anyString());
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void finishMfaEnrollment_whenCodeWrong_incrementsAttemptCount() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);
        stubEmailMfaChallenge("challenge", "ENROLL", 7L,
                AuthService.hash("challenge:123456"), 0);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.finishMfaEnrollment(7L, "challenge", "000000"));

        assertEquals("INVALID_MFA_CODE", error.code());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("attempts=attempts+1"),
                eq(AuthService.hash("challenge")));
    }

    @Test
    void finishMfaEnrollment_whenChallengeBelongsToOtherUser_rejects() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);
        stubEmailMfaChallenge("challenge", "ENROLL", 8L,
                AuthService.hash("challenge:123456"), 0);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.finishMfaEnrollment(7L, "challenge", "123456"));

        assertEquals("INVALID_MFA_CHALLENGE", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("mfa_enabled=true"), eq(7L));
    }

    @Test
    void verifyLoginMfa_whenChallengeUnknown_rejects() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.verifyLoginMfa("expired", "123456", "127.0.0.1", "agent"));

        assertEquals("INVALID_MFA_CHALLENGE", error.code());
        verify(limits).check("mfa-challenge", "expired", 8, 900);
    }

    @Test
    void verifyLoginMfa_whenEmailCodeValid_createsSession() throws Exception {
        stubEmailMfaChallenge("challenge", "LOGIN", 7L,
                AuthService.hash("challenge:123456"), 0);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("EMAIL");
        stubStoredUserById();

        var result = service.verifyLoginMfa("challenge", "123456", "127.0.0.1", "agent");

        assertEquals(7L, result.user().id());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE email_mfa_challenges SET used_at"),
                eq(AuthService.hash("challenge")));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO user_sessions"),
                eq(7L), anyString(), any(), eq("127.0.0.1"), eq("agent"));
    }

    @Test
    void verifyLoginMfa_whenRecoveryCodeValid_consumesItAndCreatesSession() throws Exception {
        stubEmailMfaChallenge("challenge", "LOGIN", 7L,
                AuthService.hash("challenge:123456"), 0);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("EMAIL");
        String recovery = "ABCDEF0123456789ABCDEF0123456789";
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("UPDATE mfa_recovery_codes"),
                org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq(7L),
                eq(AuthService.hash(recovery)))).thenReturn(java.util.List.of(11L));
        stubStoredUserById();

        var result = service.verifyLoginMfa("challenge", recovery, "127.0.0.1", "agent");

        assertEquals(7L, result.user().id());
        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("UPDATE mfa_recovery_codes"),
                org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq(7L), eq(AuthService.hash(recovery)));
    }

    @Test
    void verifyLoginMfa_whenTooManyWrongAttempts_rejectsAndIncrementsCounter() throws Exception {
        stubEmailMfaChallenge("challenge", "LOGIN", 7L,
                AuthService.hash("challenge:123456"), 5);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("EMAIL");

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.verifyLoginMfa("challenge", "123456", "127.0.0.1", "agent"));

        assertEquals("INVALID_MFA_CODE", error.code());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("attempts=attempts+1"),
                eq(AuthService.hash("challenge")));
    }

    @Test
    void disableMfa_whenPasswordWrong_doesNotDisable() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("wrong", "hash")).thenReturn(false);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.disableMfa(7L, "wrong", "challenge", "123456"));

        assertEquals("INVALID_CURRENT_PASSWORD", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("mfa_enabled=false"), eq(7L));
    }

    @Test
    void disableMfa_whenStepUpChallengeExpired_doesNotDisable() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.disableMfa(7L, "correct", "expired", "123456"));

        assertEquals("INVALID_MFA_CHALLENGE", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("mfa_enabled=false"), eq(7L));
    }

    @Test
    void disableMfa_whenStepUpValid_removesAuthenticatorAndRecoveryCodes() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L)))
                .thenReturn("hash", "EMAIL", "long@example.com");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        stubEmailMfaChallenge("challenge", "STEP_UP", 7L,
                AuthService.hash("challenge:123456"), 0);

        service.disableMfa(7L, "correct", "challenge", "123456");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("mfa_enabled=false"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("DELETE FROM totp_credentials"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE mfa_recovery_codes"), eq(7L));
    }

    @Test
    void recoveryCodesRemaining_whenCountMissing_returnsZero() {
        assertEquals(0, service.recoveryCodesRemaining(7L));
    }

    @Test
    void recoveryCodesRemaining_whenCodesExist_returnsCount() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(8);

        assertEquals(8, service.recoveryCodesRemaining(7L));
    }

    @Test
    void regenerateRecoveryCodes_whenMfaDisabled_rejectsBeforeDeletingOldCodes() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.regenerateRecoveryCodes(7L, "correct", "challenge", "123456"));

        assertEquals("MFA_NOT_ENABLED", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("UPDATE mfa_recovery_codes"), eq(7L));
    }

    @Test
    void regenerateRecoveryCodes_whenStepUpValid_replacesEightCodes() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L)))
                .thenReturn("hash", "EMAIL", "long@example.com");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(true);
        stubEmailMfaChallenge("challenge", "STEP_UP", 7L,
                AuthService.hash("challenge:123456"), 0);

        var codes = service.regenerateRecoveryCodes(7L, "correct", "challenge", "123456");

        assertEquals(8, codes.size());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE mfa_recovery_codes"), eq(7L));
        verify(jdbc, org.mockito.Mockito.times(8)).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO mfa_recovery_codes"), eq(7L), anyString());
    }

    @Test
    void startMfaStepUp_whenMfaDisabled_rejectsAfterPasswordCheck() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.startMfaStepUp(7L, "correct"));

        assertEquals("MFA_NOT_ENABLED", error.code());
    }

    @Test
    void startMfaStepUp_whenEmailMethod_sendsChallenge() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L)))
                .thenReturn("hash", "EMAIL", "long@example.com");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(true);

        var result = service.startMfaStepUp(7L, "correct");

        assertEquals("EMAIL", result.mfaMethod());
        assertEquals(false, result.emailDeliveryFailed());
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void startMfaStepUp_whenTotpMethod_issuesChallengeWithoutMail() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L)))
                .thenReturn("hash", "TOTP");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(true);

        var result = service.startMfaStepUp(7L, "correct");

        assertEquals("TOTP", result.mfaMethod());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void startTotpEnrollment_whenEmailMfaAlreadyEnabled_rejects() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(true);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.startTotpEnrollment(7L, "correct"));

        assertEquals("MFA_ALREADY_ENABLED", error.code());
        verify(totp, never()).newSecret();
    }

    @Test
    void startTotpEnrollment_whenAllowed_returnsSecretAndUri() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L)))
                .thenReturn("hash", "long@example.com");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);
        when(totp.newSecret()).thenReturn("SECRET");
        when(totp.encrypt("SECRET")).thenReturn("encrypted");
        when(totp.otpauthUri("long@example.com", "SECRET")).thenReturn("otpauth://totp/FORME");

        var result = service.startTotpEnrollment(7L, "correct");

        assertEquals("SECRET", result.secret());
        assertEquals("otpauth://totp/FORME", result.otpauthUri());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO totp_credentials"),
                eq(7L), eq("encrypted"));
    }

    @Test
    void confirmTotpEnrollment_whenNoPendingSetup_rejects() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.confirmTotpEnrollment(7L, "123456"));

        assertEquals("INVALID_TOTP_ENROLLMENT", error.code());
        verify(limits).check("totp-enroll", "7", 10, 900);
    }

    @Test
    void confirmTotpEnrollment_whenCodeWrong_doesNotEnableMfa() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn("encrypted");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(7L)))
                .thenAnswer(call -> java.util.List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(7L))).thenReturn(false);
        when(totp.decrypt("encrypted")).thenReturn("SECRET");
        when(totp.validStep(eq("SECRET"), eq("000000"), any(), eq(null))).thenReturn(-1L);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.confirmTotpEnrollment(7L, "000000"));

        assertEquals("INVALID_MFA_CODE", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("mfa_enabled=true"), eq(7L));
    }

    @Test
    void mfaEnabled_whenDatabaseValueNull_returnsFalse() {
        assertFalse(service.mfaEnabled(7L));
        verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(7L));
    }

    @Test
    void revokeSession_targetsOnlyOwnerAndSession() {
        service.revokeSession(7L, 99L);

        verify(jdbc).update(anyString(), eq(99L), eq(7L));
    }

    @Test
    void revokeOtherSessions_preservesOnlyCurrentToken() {
        service.revokeOtherSessions(7L, "current-token");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("token_hash<>?"),
                eq(7L), eq(AuthService.hash("current-token")));
    }

    private void runTransactionCallback() {
        doAnswer(call -> {
            TransactionCallback<?> callback = call.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactions).execute(any());
    }

    private void stubEmailMfaChallenge(String challenge, String purpose, long ownerId,
                                       String codeHash, int attempts) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(ownerId);
        when(row.getString(2)).thenReturn(codeHash);
        when(row.getInt(3)).thenReturn(attempts);
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("FROM email_mfa_challenges"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(AuthService.hash(challenge)), eq(purpose)))
                .thenAnswer(call -> java.util.List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
    }

    private void stubStoredUser(String email) throws Exception {
        stubStoredUser(email, "ACTIVE", true);
    }

    private void stubStoredUser(String email, String status, boolean verified) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(7L);
        when(row.getString("email")).thenReturn(email);
        when(row.getString("password_hash")).thenReturn("hash");
        when(row.getString("full_name")).thenReturn("Long");
        when(row.getString("role")).thenReturn("CUSTOMER");
        when(row.getString("status")).thenReturn(status);
        when(row.getTimestamp("verified_at")).thenReturn(verified ? Timestamp.from(Instant.now()) : null);
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("FROM users WHERE email"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(email)))
                .thenAnswer(call -> java.util.List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
    }

    private void stubStoredUserById() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(7L);
        when(row.getString("email")).thenReturn("long@example.com");
        when(row.getString("password_hash")).thenReturn("hash");
        when(row.getString("full_name")).thenReturn("Long");
        when(row.getString("role")).thenReturn("CUSTOMER");
        when(row.getString("status")).thenReturn("ACTIVE");
        when(row.getTimestamp("verified_at")).thenReturn(Timestamp.from(Instant.now()));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM users WHERE id=?"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L)))
                .thenAnswer(call -> ((RowMapper<?>) call.getArgument(1)).mapRow(row, 0));
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("FROM users WHERE id=?"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L)))
                .thenAnswer(call -> java.util.List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
    }
}
