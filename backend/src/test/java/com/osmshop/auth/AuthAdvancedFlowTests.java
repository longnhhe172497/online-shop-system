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
        mvc.perform(post("/api/me/mfa/confirm").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"" + enrollmentChallenge + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/security").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnabled").value(true));

        String login = mvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("mfa-test-" + UUID.randomUUID()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String challenge = json.readTree(login).get("challengeToken").asText();
        verify(mail, org.mockito.Mockito.times(2)).send(messages.capture());
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
    }

    @Test
    void passwordChangeRevokesAllSessions() throws Exception {
        String email = "sessions-" + UUID.randomUUID() + "@example.com";
        String bearer = activeUser(email);
        mvc.perform(get("/api/me/sessions").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].current").value(true));
        mvc.perform(post("/api/me/password").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"StrongPass123\",\"newPassword\":\"NewPassword123\","
                        + "\"confirmPassword\":\"NewPassword123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
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
}
