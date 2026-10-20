package com.osmshop.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.osmshop.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Route policy only. A permitted request reaches 404 because the controllers arrive in later PRs. */
@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CatalogSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    void publicCatalogAndCartRoutesDoNotRequireLogin() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isOk());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void managerCatalogRoutesNeedTheGrantedPermission() throws Exception {
        mvc.perform(get("/api/manager/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/manager/products").header("Authorization", session("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/products").header("Authorization", session("WAREHOUSE")))
                .andExpect(status().isForbidden());
        String manager = session("MANAGER");
        mvc.perform(get("/api/manager/products").header("Authorization", manager)).andExpect(status().isNotFound());
        mvc.perform(get("/api/manager/categories").header("Authorization", manager)).andExpect(status().isNotFound());
        mvc.perform(get("/api/manager/reviews").header("Authorization", manager)).andExpect(status().isNotFound());
    }

    @Test
    void reviewRouteIsCustomerOnly() throws Exception {
        mvc.perform(post("/api/order-items/1/review").header("Authorization", session("MANAGER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/order-items/1/review").header("Authorization", session("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    private String session(String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,?,?,?,'ACTIVE',now()) RETURNING id
                """, Long.class, "catalog-" + UUID.randomUUID() + "@example.com",
                passwords.encode("Password123"), "Test User", role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 hour')",
                id, AuthService.hash(token));
        return "Bearer " + token;
    }
}