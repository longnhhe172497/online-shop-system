package com.osmshop.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class GoogleIdentityFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean GoogleIdTokenVerifier verifier;

    @Test
    void newGoogleIdentityCreatesCustomerAndReusesSameAccount() throws Exception {
        String email = "google-" + UUID.randomUUID() + "@gmail.com";
        when(verifier.verify(anyString())).thenReturn(new GoogleIdTokenVerifier.GoogleIdentity(
                "sub-" + UUID.randomUUID(), email, "Khách Google"));
        String payload = "{\"credential\":\"test-credential\"}";
        mvc.perform(post("/api/auth/browser-google-login").contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
        mvc.perform(post("/api/auth/browser-google-login").contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
        Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void existingEmailRequiresExplicitLinkAndLinkedAccountKeepsItsRole() throws Exception {
        String email = "linked-" + UUID.randomUUID() + "@example.com";
        long userId = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,?,'Staff','SUPPORT','ACTIVE',now()) RETURNING id
                """, Long.class, email, passwords.encode("StrongPass123"));
        when(verifier.verify(anyString())).thenReturn(new GoogleIdTokenVerifier.GoogleIdentity(
                "sub-" + UUID.randomUUID(), email, "Different name"));
        mvc.perform(post("/api/auth/browser-google-login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"test-credential\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_LINK_REQUIRED"));
        String passwordLogin = "{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}";
        String bearer = new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(passwordLogin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(post("/api/me/google/link").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"wrong\",\"credential\":\"test-credential\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/google/link").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"StrongPass123\",\"credential\":\"test-credential\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/google").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linked").value(true));
        mvc.perform(post("/api/auth/browser-google-login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"test-credential\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("SUPPORT"))
                .andExpect(jsonPath("$.user.id").value(userId));
    }
}
