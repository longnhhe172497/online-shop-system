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
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AuthFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    ObjectMapper json = new ObjectMapper();
    @MockitoBean JavaMailSender mail;

    private static MockHttpServletRequestBuilder postFromFreshIp(String path) {
        return post(path).with(request -> {
            request.setRemoteAddr("test-" + UUID.randomUUID());
            return request;
        });
    }

    @Test
    void registerVerifyLoginUseBearerAndLogout() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        mvc.perform(postFromFreshIp("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Test Customer", email, "StrongPass123", "StrongPass123", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        String text = message.getValue().getText();
        String token = text.substring(text.indexOf("?token=") + 7).split("\\s")[0];

        mvc.perform(postFromFreshIp("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest());

        String body = mvc.perform(postFromFreshIp("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
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
    void mailFailureKeepsPendingAccountAndAllowsAResend() throws Exception {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mail).send(any(SimpleMailMessage.class));
        String email = "mail-failure-" + UUID.randomUUID() + "@example.com";
        mvc.perform(postFromFreshIp("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Test Customer", email, "StrongPass123", "StrongPass123", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailSent").value(false));
        String status = jdbc.queryForObject("SELECT status FROM users WHERE email=?", String.class, email);
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_VERIFICATION", status);
    }

    @Test
    void mismatchedPasswordIsRejectedBeforeSendingEmail() throws Exception {
        String email = "mismatch-" + UUID.randomUUID() + "@example.com";
        mvc.perform(postFromFreshIp("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Test Customer", email, "StrongPass123", "DifferentPass123", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetPasswordRevokesSessionsAndRejectsUsedToken() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        mvc.perform(postFromFreshIp("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Test Customer", email, "OldPassword123", "OldPassword123", null))))
                .andExpect(status().isCreated());
        ArgumentCaptor<SimpleMailMessage> registrationMail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(registrationMail.capture());
        String verificationText = registrationMail.getValue().getText();
        String verificationToken = verificationText.substring(verificationText.indexOf("?token=") + 7).split("\\s")[0];
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + verificationToken + "\"}"))
                .andExpect(status().isOk());
        String loginBody = mvc.perform(postFromFreshIp("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"OldPassword123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + json.readTree(loginBody).get("accessToken").asText();

        clearInvocations(mail);
        mvc.perform(postFromFreshIp("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.PasswordResetRequest(email))))
                .andExpect(status().isAccepted());
        mvc.perform(postFromFreshIp("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
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
        mvc.perform(postFromFreshIp("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"OldPassword123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(postFromFreshIp("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
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
        mvc.perform(postFromFreshIp("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
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

    @Test
    void browserSessionUsesHttpOnlyCookieAndRequiresCsrfForWrites() throws Exception {
        String email = "cookie-" + UUID.randomUUID() + "@example.com";
        mvc.perform(postFromFreshIp("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthDtos.RegisterRequest(
                        "Cookie User", email, "StrongPass123", "StrongPass123", null))))
                .andExpect(status().isCreated());
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        String body = message.getValue().getText();
        String token = body.substring(body.indexOf("?token=") + 7).split("\\s")[0];
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")).andExpect(status().isOk());

        var loggedIn = mvc.perform(post("/api/auth/browser-login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andReturn();
        String setCookie = loggedIn.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("SameSite=Lax"));
        Cookie session = new Cookie("FORME_SESSION", setCookie.split("[=;]", 3)[1]);
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/auth/logout").cookie(session)).andExpect(status().isForbidden());

        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String csrfToken = json.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        String csrfSetCookie = csrf.getResponse().getHeader("Set-Cookie");
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", csrfSetCookie.split("[=;]", 3)[1]);
        mvc.perform(post("/api/auth/logout").cookie(session, csrfCookie)
                .header("X-XSRF-TOKEN", csrfToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    }
}
