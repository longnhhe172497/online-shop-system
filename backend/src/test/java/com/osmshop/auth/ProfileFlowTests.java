package com.osmshop.auth;

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
class ProfileFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    ObjectMapper json = new ObjectMapper();

    @Test
    void customerCanManageOnlyOwnProfileAndAddresses() throws Exception {
        Session owner = session("CUSTOMER");
        Session other = session("CUSTOMER");
        mvc.perform(get("/api/me").header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(owner.email()));
        mvc.perform(patch("/api/me").header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Updated Name\",\"phone\":\"0901234567\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("0901234567"))
                .andExpect(jsonPath("$.email").value(owner.email()));

        String firstBody = mvc.perform(post("/api/me/addresses").header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(address("Home", false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true))
                .andReturn().getResponse().getContentAsString();
        long firstId = json.readTree(firstBody).get("id").asLong();
        mvc.perform(patch("/api/me/addresses/" + firstId).header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(address("Home Updated", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientName").value("Home Updated"))
                .andExpect(jsonPath("$.isDefault").value(true));
        String secondBody = mvc.perform(post("/api/me/addresses").header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(address("Office", true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true))
                .andReturn().getResponse().getContentAsString();
        long secondId = json.readTree(secondBody).get("id").asLong();
        mvc.perform(get("/api/me/addresses").header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[1].id").value(firstId))
                .andExpect(jsonPath("$[1].isDefault").value(false));
        mvc.perform(patch("/api/me/addresses/" + firstId).header("Authorization", other.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(address("Hacked", true)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/addresses/" + secondId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/addresses/" + secondId).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/addresses").header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[0].isDefault").value(true));
        mvc.perform(get("/api/me/addresses").header("Authorization", other.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyCustomersCanUseAddressesAndLimitIsEnforced() throws Exception {
        Session admin = session("ADMIN");
        mvc.perform(get("/api/me/addresses").header("Authorization", admin.bearer()))
                .andExpect(status().isForbidden());
        Session customer = session("CUSTOMER");
        for (int i = 0; i < 10; i++) {
            jdbc.update("""
                    INSERT INTO user_addresses(user_id,recipient_name,phone,address_line,province,is_default)
                    VALUES (?,?,?,?,?,?)
                    """, customer.id(), "Recipient", "0901234567", "Street " + i, "Hanoi", i == 0);
        }
        mvc.perform(post("/api/me/addresses").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(address("Extra", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_REACHED"));
    }

    private Session session(String role) {
        String email = "profile-" + UUID.randomUUID() + "@example.com";
        Long userId = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,?,?,?,'ACTIVE',now()) RETURNING id
                """, Long.class, email, passwords.encode("Password123"), "Test User", role);
        String token = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO user_sessions(user_id,token_hash,expires_at)
                VALUES (?,?,now() + interval '1 hour')
                """, userId, AuthService.hash(token));
        return new Session(userId, email, "Bearer " + token);
    }

    private String address(String name, boolean isDefault) throws Exception {
        return json.writeValueAsString(new ProfileDtos.AddressRequest(
                name, "0901234567", "123 Main Street", "Ward 1", "District 1", "Hanoi", isDefault));
    }

    private record Session(long id, String email, String bearer) {}
}
