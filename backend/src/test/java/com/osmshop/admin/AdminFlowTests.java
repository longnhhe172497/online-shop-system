package com.osmshop.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmshop.auth.AuthService;
import java.util.Map;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AdminFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender mail;
    ObjectMapper json = new ObjectMapper();

    @Test
    void onlyAdminCanManageUsersSettingsAndAudit() throws Exception {
        String customer = session("CUSTOMER");
        mvc.perform(get("/api/admin/users").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/settings")).andExpect(status().isUnauthorized());
        String admin = session("ADMIN");
        mvc.perform(get("/api/admin/users").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/settings").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void adminUpdatesStaffWithAuditButCannotCreateActiveAccountDirectly() throws Exception {
        String admin = session("ADMIN");
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        Long id = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,phone,role,status,verified_at)
                VALUES (?,?,?,'0901234567','SUPPORT','ACTIVE',now()) RETURNING id
                """, Long.class, email, passwords.encode("Password123"), "Staff One");
        mvc.perform(post("/api/admin/users").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", email, "fullName", "Staff One", "phone", "0901234567",
                        "password", "Password123", "role", "SUPPORT"))))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/admin/users/" + id + "/role").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"WAREHOUSE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("WAREHOUSE"));
        mvc.perform(patch("/api/admin/users/" + id + "/status").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCKED"));
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action").value("USER_STATUS_CHANGED"));
        mvc.perform(patch("/api/admin/settings/max_saved_addresses").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"12\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("12"));
        mvc.perform(patch("/api/admin/settings/max_saved_addresses").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"999\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SETTING"));
    }

    @Test
    void adminInvitesStaffAndInviteeSetsOwnPassword() throws Exception {
        String admin = session("ADMIN");
        String email = "invited-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/admin/invitations").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", email, "fullName", "New Staff", "phone", "0901234567", "role", "WAREHOUSE"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("WAREHOUSE"))
                .andExpect(jsonPath("$.status").value("SENT"));
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        String text = message.getValue().getText();
        String token = text.substring(text.indexOf("?token=") + 7).split("\\s")[0];
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token,
                        "password", "OwnPassword123", "confirmPassword", "OwnPassword123"))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/invitations").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("ACCEPTED"));
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token,
                        "password", "OwnPassword123", "confirmPassword", "OwnPassword123"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("invite-" + UUID.randomUUID()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "OwnPassword123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("WAREHOUSE"));
    }

    @Test
    void failedDeliveryIsVisibleAndInvitationCannotBeAccepted() throws Exception {
        String admin = session("ADMIN");
        String email = "failed-invite-" + UUID.randomUUID() + "@example.com";
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/admin/invitations").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", email, "fullName", "New Staff", "role", "SUPPORT"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INVITATION_DELIVERY_FAILED"));
        mvc.perform(get("/api/admin/invitations").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("FAILED"));
        String status = jdbc.queryForObject("SELECT delivery_status FROM staff_invitations WHERE email=?",
                String.class, email);
        org.junit.jupiter.api.Assertions.assertEquals("FAILED", status);
    }

    @Test
    void adminCanRevokeAndResendExpiredInvitations() throws Exception {
        String admin = session("ADMIN");
        String email = "reinvite-" + UUID.randomUUID() + "@example.com";
        String created = mvc.perform(post("/api/admin/invitations").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", email, "fullName", "Staff Again", "role", "SUPPORT"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long originalId = json.readTree(created).get("id").asLong();
        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(messages.capture());
        String originalText = messages.getValue().getText();
        String originalToken = originalText.substring(originalText.indexOf("?token=") + 7).split("\\s")[0];
        mvc.perform(post("/api/admin/invitations/" + originalId + "/revoke")
                .header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", originalToken,
                        "password", "OwnPassword123", "confirmPassword", "OwnPassword123"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/invitations/" + originalId + "/resend")
                .header("Authorization", admin)).andExpect(status().isConflict());
        String expired = mvc.perform(post("/api/admin/invitations").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", email, "fullName", "Staff Again", "role", "SUPPORT"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long expiredId = json.readTree(expired).get("id").asLong();
        jdbc.update("UPDATE staff_invitations SET expires_at=now()-interval '1 minute' WHERE id=?", expiredId);
        mvc.perform(post("/api/admin/invitations/" + expiredId + "/resend")
                .header("Authorization", admin)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"));
        mvc.perform(get("/api/admin/invitations").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].status").value("REVOKED"));
    }

    @Test
    void pendingInvitationOlderThanFiveMinutesCanBeSafelyReplaced() throws Exception {
        String admin = session("ADMIN");
        String email = "stalled-" + UUID.randomUUID() + "@example.com";
        String created = mvc.perform(post("/api/admin/invitations").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", email, "fullName", "Stalled Staff", "role", "SUPPORT"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long oldId = json.readTree(created).get("id").asLong();
        jdbc.update("""
                UPDATE staff_invitations SET delivery_status='PENDING',created_at=now()-interval '6 minutes'
                WHERE id=?
                """, oldId);
        mvc.perform(get("/api/admin/invitations").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("STALLED"));
        mvc.perform(post("/api/admin/invitations/" + oldId + "/resend")
                .header("Authorization", admin)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"));
        mvc.perform(get("/api/admin/invitations").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].status").value("REVOKED"));
    }

    @Test
    void adminCanInspectUserRevokeSessionsAndFilterAudit() throws Exception {
        String admin = session("ADMIN");
        String bearer = session("SUPPORT");
        String token = bearer.substring("Bearer ".length());
        long userId = jdbc.queryForObject("SELECT user_id FROM user_sessions WHERE token_hash=?",
                Long.class, AuthService.hash(token));
        mvc.perform(get("/api/admin/users/" + userId).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/" + userId).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessions.length()").value(1));
        mvc.perform(post("/api/admin/users/" + userId + "/sessions/revoke")
                .header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users/" + userId).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessions.length()").value(0));
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/audit-logs?action=USER_SESSIONS_REVOKED")
                .header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("USER_SESSIONS_REVOKED"));
        mvc.perform(get("/api/admin/audit-logs?from=2026-10-12&to=2026-10-11")
                .header("Authorization", admin)).andExpect(status().isBadRequest());
    }

    private String session(String role) {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        Long id = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,?,?,?,'ACTIVE',now()) RETURNING id
                """, Long.class, email, passwords.encode("Password123"), "Test User", role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 hour')",
                id, AuthService.hash(token));
        return "Bearer " + token;
    }
}
