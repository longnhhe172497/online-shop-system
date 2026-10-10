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
    void adminCreatesAndUpdatesStaffWithAudit() throws Exception {
        String admin = session("ADMIN");
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        String created = mvc.perform(post("/api/admin/users").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", email, "fullName", "Staff One", "phone", "0901234567",
                        "password", "Password123", "role", "SUPPORT"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("SUPPORT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
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
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("WAREHOUSE"));
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        String text = message.getValue().getText();
        String token = text.substring(text.indexOf("?token=") + 7).split("\\s")[0];
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token,
                        "password", "OwnPassword123", "confirmPassword", "OwnPassword123"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token,
                        "password", "OwnPassword123", "confirmPassword", "OwnPassword123"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("invite-" + UUID.randomUUID()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "OwnPassword123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("WAREHOUSE"));
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
