package com.osmshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class EmailChangeBoundaryTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender mail;

    @Test
    void wrongPasswordAndAlreadyRegisteredEmailDoNotIssueLinks() throws Exception {
        Session owner = session();
        String existing = "existing-email-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users(email,password_hash,full_name,role,status,verified_at) "
                + "VALUES (?,?,?,'CUSTOMER','ACTIVE',now())",
                existing, passwords.encode("Password123"), "Existing");
        mvc.perform(post("/api/me/email-change").header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("new-" + UUID.randomUUID() + "@example.com", "wrong")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        mvc.perform(post("/api/me/email-change").header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(payload(existing, "Password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM email_change_requests WHERE user_id=?",
                Integer.class, owner.id()));
    }

    @Test
    void emailChangesOnlyAfterLinkConfirmationAndRevokesExistingSession() throws Exception {
        Session owner = session();
        String next = "new-email-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/me/email-change").header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(payload(next, "Password123")))
                .andExpect(status().isAccepted());
        assertEquals(owner.email(), jdbc.queryForObject("SELECT email FROM users WHERE id=?",
                String.class, owner.id()));
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertEquals(next, sent.getValue().getTo()[0]);
        String message = sent.getValue().getText();
        String token = message.substring(message.indexOf("?token=") + 7).split("\\s")[0];
        mvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        assertEquals(next, jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, owner.id()));
        mvc.perform(get("/api/me").header("Authorization", owner.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_CHANGE_TOKEN"));
    }

    @Test
    void expiredEmailChangeLinkCannotModifyAccount() throws Exception {
        Session owner = session();
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO email_change_requests"
                + "(user_id,new_email,token_hash,created_at,expires_at) "
                + "VALUES (?,?,?,now()-interval '2 hours',now()-interval '1 minute')",
                owner.id(), "expired-" + UUID.randomUUID() + "@example.com", AuthService.hash(token));
        mvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_CHANGE_TOKEN"));
        assertEquals(owner.email(), jdbc.queryForObject("SELECT email FROM users WHERE id=?",
                String.class, owner.id()));
    }

    private Session session() {
        String email = "email-change-" + UUID.randomUUID() + "@example.com";
        long id = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,role,status,verified_at) "
                + "VALUES (?,?,?,'CUSTOMER','ACTIVE',now()) RETURNING id", Long.class,
                email, passwords.encode("Password123"), "Test User");
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) "
                + "VALUES (?,?,now()+interval '1 hour')", id, AuthService.hash(token));
        return new Session(id, email, "Bearer " + token);
    }

    private static String payload(String email, String password) {
        return "{\"newEmail\":\"" + email + "\",\"currentPassword\":\"" + password + "\"}";
    }

    private record Session(long id, String email, String bearer) {}
}
