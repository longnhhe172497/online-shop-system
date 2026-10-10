package com.osmshop.auth;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AuthAdvancedFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired TotpService totp;
    @MockitoBean JavaMailSender mail;
    ObjectMapper json = new ObjectMapper();

    @Test
    void emailMfaCanBeEnabledAndIsRequiredAtNextLogin() throws Exception {
        String email = "mfa-" + UUID.randomUUID() + "@example.com";
        String bearer = activeUser(email);
        String start = mvc.perform(post("/api/me/mfa/start").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String enrollmentChallenge = json.readTree(start).get("challengeToken").asText();
        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(messages.capture());
        String code = messages.getValue().getText().replaceAll("(?s).*?([0-9]{6}).*", "$1");
        String enrollment = mvc.perform(post("/api/me/mfa/confirm").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"" + enrollmentChallenge + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes.length()").value(8))
                .andReturn().getResponse().getContentAsString();
        String recoveryCode = json.readTree(enrollment).get("recoveryCodes").get(0).asText();
        mvc.perform(get("/api/me/security").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnabled").value(true))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(8));

        String login = mvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("mfa-test-" + UUID.randomUUID()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String challenge = json.readTree(login).get("challengeToken").asText();
        verify(mail, org.mockito.Mockito.times(3)).send(messages.capture());
        String loginCode = messages.getAllValues().getLast().getText().replaceAll("(?s).*?([0-9]{6}).*", "$1");
        String wrongCode = "000000".equals(loginCode) ? "999999" : "000000";
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"" + challenge + "\",\"code\":\"" + wrongCode + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"" + challenge + "\",\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email));
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"" + challenge + "\",\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isBadRequest());

        String recoveryLogin = mvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("recovery-" + UUID.randomUUID()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String recoveryChallenge = json.readTree(recoveryLogin).get("challengeToken").asText();
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("challengeToken", recoveryChallenge,
                        "code", recoveryCode))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email));
        mvc.perform(get("/api/me/security").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodesRemaining").value(7));
        String nextLogin = mvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("recovery-reuse-" + UUID.randomUUID()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "challengeToken", json.readTree(nextLogin).get("challengeToken").asText(),
                        "code", recoveryCode))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recoveryCodesCanBeRegeneratedOnlyWithPassword() throws Exception {
        String email = "regenerate-" + UUID.randomUUID() + "@example.com";
        String bearer = activeUser(email);
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        jdbc.update("UPDATE users SET mfa_enabled=true WHERE id=?", userId);
        String oldCode = "0123456789ABCDEF0123456789ABCDEF";
        jdbc.update("INSERT INTO mfa_recovery_codes(user_id,code_hash) VALUES (?,?)",
                userId, AuthService.hash(oldCode));
        mvc.perform(post("/api/me/mfa/recovery/regenerate").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
        String stepUp = mvc.perform(post("/api/me/mfa/step-up/start").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(messages.capture());
        String code = messages.getValue().getText().replaceAll("(?s).*?([0-9]{6}).*", "$1");
        String challenge = json.readTree(stepUp).get("challengeToken").asText();
        String wrongCode = "000000".equals(code) ? "999999" : "000000";
        mvc.perform(post("/api/me/mfa/recovery/regenerate").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "password", "StrongPass123", "challengeToken", challenge, "code", wrongCode))))
                .andExpect(status().isBadRequest());
        Integer stillUnused = jdbc.queryForObject("""
                SELECT count(*) FROM mfa_recovery_codes WHERE user_id=? AND code_hash=? AND used_at IS NULL
                """, Integer.class, userId, AuthService.hash(oldCode));
        org.junit.jupiter.api.Assertions.assertEquals(1, stillUnused);
        mvc.perform(post("/api/me/mfa/recovery/regenerate").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "password", "StrongPass123", "challengeToken", challenge, "code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes.length()").value(8));
        Integer oldUnused = jdbc.queryForObject("""
                SELECT count(*) FROM mfa_recovery_codes WHERE user_id=? AND code_hash=? AND used_at IS NULL
                """, Integer.class, userId, AuthService.hash(oldCode));
        org.junit.jupiter.api.Assertions.assertEquals(0, oldUnused);
        mvc.perform(get("/api/me/security").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodesRemaining").value(8));
    }

    @Test
    void totpEnrollmentLoginAndStepUpRejectReusedCodes() throws Exception {
        String email = "totp-" + UUID.randomUUID() + "@example.com";
        String bearer = activeUser(email);
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        String started = mvc.perform(post("/api/me/mfa/totp/start").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.otpauthUri").exists())
                .andReturn().getResponse().getContentAsString();
        String secret = json.readTree(started).get("secret").asText();
        String encrypted = jdbc.queryForObject("SELECT pending_secret FROM totp_credentials WHERE user_id=?",
                String.class, userId);
        org.junit.jupiter.api.Assertions.assertNotEquals(secret, encrypted);
        String code = totp.codeAtStep(secret, java.time.Instant.now().getEpochSecond() / 30);
        mvc.perform(post("/api/me/mfa/totp/confirm").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes.length()").value(8));
        mvc.perform(get("/api/me/security").header("Authorization", bearer))
                .andExpect(jsonPath("$.mfaMethod").value("TOTP"));
        String login = mvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("totp-login-" + UUID.randomUUID()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaMethod").value("TOTP"))
                .andReturn().getResponse().getContentAsString();
        String challenge = json.readTree(login).get("challengeToken").asText();
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("challengeToken", challenge, "code", code))))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE totp_credentials SET last_used_step=NULL WHERE user_id=?", userId);
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("challengeToken", challenge, "code", code))))
                .andExpect(status().isOk());
        String stepUp = mvc.perform(post("/api/me/mfa/step-up/start").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaMethod").value("TOTP"))
                .andReturn().getResponse().getContentAsString();
        String stepChallenge = json.readTree(stepUp).get("challengeToken").asText();
        mvc.perform(post("/api/me/mfa/disable").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "password", "StrongPass123", "challengeToken", stepChallenge, "code", code))))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE totp_credentials SET last_used_step=NULL WHERE user_id=?", userId);
        mvc.perform(post("/api/me/mfa/disable").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "password", "StrongPass123", "challengeToken", stepChallenge, "code", code))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/security").header("Authorization", bearer))
                .andExpect(jsonPath("$.mfaEnabled").value(false));
    }

    @Test
    void passwordChangeRevokesAllSessions() throws Exception {
        String email = "sessions-" + UUID.randomUUID() + "@example.com";
        String bearer = activeUser(email);
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        String emailToken = pendingSecurityActions(userId);
        mvc.perform(get("/api/me/sessions").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].current").value(true));
        mvc.perform(post("/api/me/password").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"StrongPass123\",\"newPassword\":\"NewPassword123\","
                        + "\"confirmPassword\":\"NewPassword123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        assertSecurityActionsInvalidated(userId, emailToken);
    }

    @Test
    void passwordResetInvalidatesPendingEmailChangeAndMfa() throws Exception {
        String email = "reset-actions-" + UUID.randomUUID() + "@example.com";
        activeUser(email);
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        String emailToken = pendingSecurityActions(userId);
        String resetToken = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO auth_tokens(user_id,token_hash,purpose,expires_at)
                VALUES (?,?,'PASSWORD_RESET',now()+interval '30 minutes')
                """, userId, AuthService.hash(resetToken));
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("token", resetToken,
                        "newPassword", "ResetPassword123", "confirmPassword", "ResetPassword123"))))
                .andExpect(status().isOk());
        assertSecurityActionsInvalidated(userId, emailToken);
    }

    @Test
    void authenticatedRequestRefreshesLastSeenAndLoginFailureIsAudited() throws Exception {
        String email = "activity-" + UUID.randomUUID() + "@example.com";
        String bearer = activeUser(email);
        String token = bearer.substring("Bearer ".length());
        jdbc.update("""
                UPDATE user_sessions SET last_seen_at=now()-interval '10 minutes' WHERE token_hash=?
                """, AuthService.hash(token));
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isOk());
        Integer refreshed = jdbc.queryForObject("""
                SELECT count(*) FROM user_sessions
                WHERE token_hash=? AND last_seen_at>now()-interval '5 minutes'
                """, Integer.class, AuthService.hash(token));
        org.junit.jupiter.api.Assertions.assertEquals(1, refreshed);
        mvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("audit-" + UUID.randomUUID()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        Integer failures = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE action='AUTH_LOGIN_FAILED' AND entity_type='USER' AND entity_id=?
                """, Integer.class, Long.toString(userId));
        org.junit.jupiter.api.Assertions.assertEquals(1, failures);
    }

    @Test
    void idleSessionIsRejectedAndRevokedOnTheServer() throws Exception {
        String bearer = activeUser("idle-" + UUID.randomUUID() + "@example.com");
        String tokenHash = AuthService.hash(bearer.substring("Bearer ".length()));
        jdbc.update("UPDATE user_sessions SET last_seen_at=now()-interval '31 minutes' WHERE token_hash=?",
                tokenHash);
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        Integer revoked = jdbc.queryForObject("""
                SELECT count(*) FROM user_sessions WHERE token_hash=? AND revoked_at IS NOT NULL
                """, Integer.class, tokenHash);
        org.junit.jupiter.api.Assertions.assertEquals(1, revoked);
    }

    @Test
    void emailChangeRequiresNewAddressConfirmationAndRevokesSessions() throws Exception {
        String oldEmail = "old-" + UUID.randomUUID() + "@example.com";
        String newEmail = "new-" + UUID.randomUUID() + "@example.com";
        String bearer = activeUser(oldEmail);
        mvc.perform(post("/api/me/email-change").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/email-change").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"StrongPass123\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(messages.capture());
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[]{newEmail}, messages.getValue().getTo());
        String link = messages.getValue().getText();
        String token = link.substring(link.indexOf("?token=") + 7).split("\\s")[0];
        mvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest());
        String savedEmail = jdbc.queryForObject("SELECT email FROM users WHERE email=?", String.class, newEmail);
        org.junit.jupiter.api.Assertions.assertEquals(newEmail, savedEmail);
    }

    private String activeUser(String email) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,?,?,'ADMIN','ACTIVE',now()) RETURNING id
                """, Long.class, email, passwords.encode("StrongPass123"), "Admin Test");
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 hour')",
                id, AuthService.hash(token));
        return "Bearer " + token;
    }

    private String pendingSecurityActions(long userId) {
        String emailToken = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO email_change_requests(user_id,new_email,token_hash,expires_at)
                VALUES (?,?,?,now()+interval '1 hour')
                """, userId, "new-" + UUID.randomUUID() + "@example.com", AuthService.hash(emailToken));
        jdbc.update("""
                INSERT INTO email_mfa_challenges(user_id,challenge_hash,code_hash,purpose,expires_at)
                VALUES (?,?,?,'LOGIN',now()+interval '10 minutes')
                """, userId, AuthService.hash(UUID.randomUUID().toString()), AuthService.hash("123456"));
        return emailToken;
    }

    private void assertSecurityActionsInvalidated(long userId, String emailToken) throws Exception {
        Integer emailPending = jdbc.queryForObject("""
                SELECT count(*) FROM email_change_requests WHERE user_id=? AND used_at IS NULL
                """, Integer.class, userId);
        Integer mfaPending = jdbc.queryForObject("""
                SELECT count(*) FROM email_mfa_challenges WHERE user_id=? AND used_at IS NULL
                """, Integer.class, userId);
        org.junit.jupiter.api.Assertions.assertEquals(0, emailPending);
        org.junit.jupiter.api.Assertions.assertEquals(0, mfaPending);
        mvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + emailToken + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
