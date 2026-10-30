package com.osmshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ProfileBoundaryTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void profileAndAddressFormsRejectInvalidPayloads() throws Exception {
        Session customer = session("CUSTOMER");
        mvc.perform(patch("/api/me").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/addresses").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipientName\":\"Person\","
                        + "\"phone\":\"\",\"addressLine\":\"Street\",\"province\":\"Hanoi\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/addresses").header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/me/security").header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnabled").value(false))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(0));
        mvc.perform(get("/api/me/google").header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linked").value(false));
    }

    @Test
    void missingAddressIsNotVisibleOrEditableByAnotherCustomer() throws Exception {
        Session owner = session("CUSTOMER");
        Session other = session("CUSTOMER");
        long addressId = jdbc.queryForObject("INSERT INTO user_addresses"
                + "(user_id,recipient_name,phone,address_line,province,is_default)"
                + " VALUES (?,?,?,?,?,true) RETURNING id", Long.class,
                owner.id(), "Owner", "0901234567", "Street", "Hanoi");
        mvc.perform(patch("/api/me/addresses/" + addressId).header("Authorization", other.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipientName\":\"Intruder\","
                        + "\"phone\":\"0901234567\",\"addressLine\":\"Street\","
                        + "\"province\":\"Hanoi\",\"isDefault\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/addresses/" + addressId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        assertEquals("Owner", jdbc.queryForObject("SELECT recipient_name FROM user_addresses WHERE id=?",
                String.class, addressId));
    }

    @Test
    void userCanRevokeOtherSessionsButCannotRevokeAnotherUsersSession() throws Exception {
        Session owner = session("CUSTOMER");
        Session second = sessionFor(owner.id());
        Session other = session("CUSTOMER");
        long otherSessionId = jdbc.queryForObject("SELECT id FROM user_sessions WHERE token_hash=?",
                Long.class, AuthService.hash(other.bearer().substring(7)));
        JsonNode sessions = json.readTree(mvc.perform(get("/api/me/sessions")
                .header("Authorization", owner.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andReturn().getResponse().getContentAsString());
        long currentCount = 0;
        for (JsonNode item : sessions) if (item.get("current").asBoolean()) currentCount++;
        assertEquals(1, currentCount);
        mvc.perform(delete("/api/me/sessions/" + otherSessionId)
                .header("Authorization", owner.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", other.bearer())).andExpect(status().isOk());
        mvc.perform(post("/api/me/sessions/revoke-other").header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", owner.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", second.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", other.bearer())).andExpect(status().isOk());
    }

    private Session session(String role) {
        long id = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,role,status,verified_at) "
                + "VALUES (?,?,?,?,'ACTIVE',now()) RETURNING id", Long.class,
                "profile-boundary-" + UUID.randomUUID() + "@example.com", passwords.encode("Password123"),
                "Test User", role);
        return sessionFor(id);
    }

    private Session sessionFor(long id) {
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) "
                + "VALUES (?,?,now()+interval '1 hour')", id, AuthService.hash(token));
        return new Session(id, "Bearer " + token);
    }

    private record Session(long id, String bearer) {}
}
