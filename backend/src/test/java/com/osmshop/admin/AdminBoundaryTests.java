package com.osmshop.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmshop.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
class AdminBoundaryTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender mail;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void allAdminAreasRejectCustomerAndAnonymousRequests() throws Exception {
        String customer = session("CUSTOMER");
        for (String path : new String[] {"/api/admin/users", "/api/admin/settings", "/api/admin/audit-logs",
                "/api/admin/invitations", "/api/admin/role-permissions"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", customer)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/invitations").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/settings/max_saved_addresses").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"5\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void userListSearchesAndPaginatesAndRejectsInvalidPage() throws Exception {
        String admin = session("ADMIN");
        String marker = "search" + UUID.randomUUID().toString().replace("-", "");
        user(marker + "-one@example.com", "First " + marker, "SUPPORT");
        user(marker + "-two@example.com", "Second " + marker, "WAREHOUSE");
        mvc.perform(get("/api/admin/users").header("Authorization", admin)
                .param("search", marker).param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/admin/users").header("Authorization", admin)
                .param("search", marker).param("role", "WAREHOUSE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].email").value(marker + "-two@example.com"));
        mvc.perform(get("/api/admin/users").header("Authorization", admin).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", admin).param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
    }

    @Test
    void userEditsValidateInputAndMissingTargets() throws Exception {
        String admin = session("ADMIN");
        long target = user("edited-" + UUID.randomUUID() + "@example.com", "Before", "SUPPORT");
        mvc.perform(patch("/api/admin/users/" + target).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"  After  \",\"phone\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("After"));
        mvc.perform(patch("/api/admin/users/" + target).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/users/" + target + "/role").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        mvc.perform(patch("/api/admin/users/" + target + "/status").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STATUS"));
        mvc.perform(get("/api/admin/users/999999999").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void lastActiveAdminCannotLoseRoleOrBeLocked() throws Exception {
        // The transaction rolls this setup back; the production seed/admin accounts remain unchanged.
        jdbc.update("UPDATE users SET status='INACTIVE' WHERE role='ADMIN' AND status='ACTIVE'");
        String admin = session("ADMIN");
        long id = jdbc.queryForObject("SELECT user_id FROM user_sessions WHERE token_hash=?", Long.class,
                AuthService.hash(admin.substring(7)));
        mvc.perform(patch("/api/admin/users/" + id + "/role").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPPORT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        mvc.perform(patch("/api/admin/users/" + id + "/status").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        mvc.perform(get("/api/admin/users/" + id).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));
    }

    @Test
    void invitationRejectsCustomerRoleExistingEmailAndMismatchedPassword() throws Exception {
        String admin = session("ADMIN");
        String email = "invite-boundary-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/admin/invitations").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"fullName\":\"Person\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        verify(mail, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        user(email, "Existing", "SUPPORT");
        mvc.perform(post("/api/admin/invitations").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"fullName\":\"Person\",\"role\":\"SUPPORT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"forged\",\"password\":\"Password123\","
                        + "\"confirmPassword\":\"Different123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"forged\",\"password\":\"Password123\","
                        + "\"confirmPassword\":\"Password123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INVITATION"));
    }

    @Test
    void permissionsRejectDuplicatesAndNoopDoesNotIncrementVersion() throws Exception {
        String admin = session("ADMIN");
        JsonNode matrix = json.readTree(mvc.perform(get("/api/admin/role-permissions")
                .header("Authorization", admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode warehouse = null;
        for (JsonNode role : matrix.get("roles")) if ("WAREHOUSE".equals(role.get("role").asText())) warehouse = role;
        int version = warehouse.get("version").asInt();
        mvc.perform(put("/api/admin/role-permissions/WAREHOUSE").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        java.util.Map.of("version", version, "permissions", java.util.List.of(
                                "WAREHOUSE_INVENTORY_VIEW", "WAREHOUSE_INVENTORY_VIEW")))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERMISSION"));
        mvc.perform(put("/api/admin/role-permissions/WAREHOUSE").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        java.util.Map.of("version", version, "permissions", warehouse.get("permissions")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(version));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='ROLE_PERMISSIONS_CHANGED' "
                + "AND entity_id='WAREHOUSE' AND actor_id=(SELECT id FROM users WHERE email=?)",
                Integer.class, emailFor(admin)));
    }

    private long user(String email, String name, String role) {
        return jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,role,status,verified_at) "
                + "VALUES (?,?,?,?,'ACTIVE',now()) RETURNING id", Long.class,
                email, passwords.encode("Password123"), name, role);
    }

    private String session(String role) {
        long id = user("admin-boundary-" + UUID.randomUUID() + "@example.com", "Test User", role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) "
                + "VALUES (?,?,now()+interval '1 hour')", id, AuthService.hash(token));
        return "Bearer " + token;
    }

    private String emailFor(String bearer) {
        return jdbc.queryForObject("SELECT u.email FROM users u JOIN user_sessions s ON s.user_id=u.id "
                + "WHERE s.token_hash=?", String.class, AuthService.hash(bearer.substring(7)));
    }
}
