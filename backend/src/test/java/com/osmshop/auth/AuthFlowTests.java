package com.osmshop.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AuthFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    ObjectMapper json = new ObjectMapper();
    @MockitoBean JavaMailSender mail;

    @Test
    void registerVerifyLoginUseBearerAndLogout() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Test Customer", email, "StrongPass123", "StrongPass123", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        String text = message.getValue().getText();
        String token = text.substring(text.indexOf("?token=") + 7).trim();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest());

        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(body);
        String bearer = "Bearer " + result.get("accessToken").asText();
        mvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/admin/users").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void mailFailureReturnsDeliveryErrorInsteadOfAuthenticationError() throws Exception {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mail).send(any(SimpleMailMessage.class));
        String email = "mail-failure-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Test Customer", email, "StrongPass123", "StrongPass123", null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED"));
    }

    @Test
    void mismatchedPasswordIsRejectedBeforeSendingEmail() throws Exception {
        String email = "mismatch-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Test Customer", email, "StrongPass123", "DifferentPass123", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetPasswordRevokesSessionsAndRejectsUsedToken() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Test Customer", email, "OldPassword123", "OldPassword123", null))))
                .andExpect(status().isCreated());
        ArgumentCaptor<SimpleMailMessage> registrationMail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(registrationMail.capture());
        String verificationText = registrationMail.getValue().getText();
        String verificationToken = verificationText.substring(verificationText.indexOf("?token=") + 7).trim();
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + verificationToken + "\"}"))
                .andExpect(status().isOk());
        String loginBody = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"OldPassword123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + json.readTree(loginBody).get("accessToken").asText();

        clearInvocations(mail);
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.PasswordResetRequest(email))))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.PasswordResetRequest(email))))
                .andExpect(status().isAccepted());
        ArgumentCaptor<SimpleMailMessage> resetMail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(resetMail.capture());
        String resetText = resetMail.getValue().getText();
        String resetToken = resetText.substring(resetText.indexOf("?token=") + 7).split("\\s")[0];

        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.PasswordResetConfirmRequest(
                        resetToken, "NewPassword123", "WrongPassword123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.PasswordResetConfirmRequest(
                        resetToken, "NewPassword123", "NewPassword123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reset").value(true));
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"OldPassword123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"NewPassword123\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.PasswordResetConfirmRequest(
                        resetToken, "AnotherPassword123", "AnotherPassword123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void resetRequestForUnknownEmailDoesNotRevealAccountOrSendMail() throws Exception {
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void forgedResetTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.PasswordResetConfirmRequest(
                        "not-a-real-token", "NewPassword123", "NewPassword123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }
}
