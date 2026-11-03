package com.osmshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthRateLimiterTests {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final AuthRateLimiter limiter = new AuthRateLimiter(jdbc);

    @Test
    void normalizesIdentityAndStoresOnlyDigest() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
                .thenReturn(1);

        limiter.check("login", "  PERSON@Example.COM ", 5, 60);

        ArgumentCaptor<Object> argument = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), argument.capture(),
                argument.capture(), argument.capture());
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("login:person@example.com".getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, argument.getAllValues().get(0));
        assertEquals(60, argument.getAllValues().get(1));
        assertEquals(60, argument.getAllValues().get(2));
    }

    @Test
    void rejectsAttemptAfterConfiguredMaximum() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
                .thenReturn(3, 4);

        limiter.check("reset", "person@example.com", 3, 300);
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> limiter.check("reset", "person@example.com", 3, 300));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.status());
        assertEquals("TOO_MANY_REQUESTS", error.code());
    }
}
