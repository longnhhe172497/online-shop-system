package com.osmshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class AuthBoundaryTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender mail;

    @Test
    void registrationValidatesInputAndRejectsDuplicateEmailWithoutMail() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users(email,password_hash,full_name,role,status) "
                + "VALUES (?,?,?,'CUSTOMER','PENDING_VERIFICATION')",
                email, passwords.encode("Password123"), "Existing");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"A\",\"email\":\"not-an-email\","
                        + "\"password\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Another\",\"email\":\"" + email + "\","
                        + "\"password\":\"Password123\",\"confirmPassword\":\"Password123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void expiredVerificationAndResetLinksCannotBeUsed() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.com";
        long id = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,role,status) "
                + "VALUES (?,?,?,'CUSTOMER','PENDING_VERIFICATION') RETURNING id",
                Long.class, email, passwords.encode("Password123"), "Expired");
        String verification = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO auth_tokens(user_id,token_hash,purpose,created_at,expires_at) "
                + "VALUES (?,?,'EMAIL_VERIFICATION',now()-interval '2 hours',now()-interval '1 minute')",
                id, AuthService.hash(verification));
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + verification + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
        assertEquals("PENDING_VERIFICATION",
                jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, id));

        jdbc.update("UPDATE users SET status='ACTIVE',verified_at=now() WHERE id=?", id);
        String reset = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO auth_tokens(user_id,token_hash,purpose,created_at,expires_at) "
                + "VALUES (?,?,'PASSWORD_RESET',now()-interval '2 hours',now()-interval '1 minute')",
                id, AuthService.hash(reset));
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + reset + "\",\"newPassword\":\"NewPassword123\","
                        + "\"confirmPassword\":\"NewPassword123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void resendDoesNotExposeUnknownEmailAndObservesCooldown() throws Exception {
        String unknown = "unknown-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/verification/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + unknown + "\"}"))
                .andExpect(status().isAccepted());
        verify(mail, never()).send(any(SimpleMailMessage.class));

        String email = "pending-" + UUID.randomUUID() + "@example.com";
        long id = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,role,status) "
                + "VALUES (?,?,?,'CUSTOMER','PENDING_VERIFICATION') RETURNING id",
                Long.class, email, passwords.encode("Password123"), "Pending");
        jdbc.update("INSERT INTO auth_tokens(user_id,token_hash,purpose,expires_at) "
                + "VALUES (?,?,'EMAIL_VERIFICATION',now()+interval '1 hour')", id,
                AuthService.hash(UUID.randomUUID().toString()));
        mvc.perform(post("/api/auth/verification/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        verify(mail, never()).send(any(SimpleMailMessage.class));
        jdbc.update("UPDATE auth_tokens SET created_at=now()-interval '6 minutes' WHERE user_id=?", id);
        mvc.perform(post("/api/auth/verification/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertEquals(email, sent.getValue().getTo()[0]);
    }

    @Test
    void lockedAccountCannotLoginEvenWithCorrectPassword() throws Exception {
        String email = "locked-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users(email,password_hash,full_name,role,status,verified_at) "
                + "VALUES (?,?,?,'CUSTOMER','LOCKED',now())", email, passwords.encode("Password123"), "Locked");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
