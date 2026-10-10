package com.osmshop.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Database-backed, atomic limits shared by all local application processes. */
@Component
public class AuthRateLimiter {
    private final JdbcTemplate jdbc;

    public AuthRateLimiter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void check(String action, String identity, int maximum, int windowSeconds) {
        String key = digest(action + ":" + identity.trim().toLowerCase(Locale.ROOT));
        Integer attempts = jdbc.queryForObject("""
                INSERT INTO auth_rate_limits(limit_key,attempt_count,window_started_at)
                VALUES (?,1,now())
                ON CONFLICT(limit_key) DO UPDATE SET
                  attempt_count = CASE WHEN auth_rate_limits.window_started_at <= now() - (? * interval '1 second')
                    THEN 1 ELSE auth_rate_limits.attempt_count + 1 END,
                  window_started_at = CASE WHEN auth_rate_limits.window_started_at <= now() - (? * interval '1 second')
                    THEN now() ELSE auth_rate_limits.window_started_at END
                RETURNING attempt_count
                """, Integer.class, key, windowSeconds, windowSeconds);
        if (attempts != null && attempts > maximum) {
            throw new AuthApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "Bạn đã thử quá nhiều lần. Vui lòng chờ ít phút rồi thử lại.");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
