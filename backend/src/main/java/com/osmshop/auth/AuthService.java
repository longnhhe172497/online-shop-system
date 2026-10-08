package com.osmshop.auth;

import com.osmshop.auth.AuthDtos.LoginRequest;
import com.osmshop.auth.AuthDtos.LoginResponse;
import com.osmshop.auth.AuthDtos.RegisterRequest;
import com.osmshop.auth.AuthDtos.RegisterResponse;
import com.osmshop.auth.AuthDtos.UserResponse;
import com.osmshop.auth.AuthDtos.VerifyResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JavaMailSender mail;
    private final String mailFrom;
    private final String frontendBaseUrl;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords, JavaMailSender mail,
                       @Value("${app.mail-from}") String mailFrom,
                       @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.mail = mail;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        try {
            Long userId = jdbc.queryForObject("""
                    INSERT INTO users(email,password_hash,full_name,phone,role,status)
                    VALUES (?, ?, ?, ?, 'CUSTOMER', 'PENDING_VERIFICATION') RETURNING id
                    """, Long.class, email, passwords.encode(request.password()),
                    request.fullName().trim(), request.phone());
            String token = newToken();
            jdbc.update("""
                    INSERT INTO auth_tokens(user_id,token_hash,purpose,expires_at)
                    VALUES (?, ?, 'EMAIL_VERIFICATION', ?)
                    """, userId, hash(token), Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("Verify your Online Shop System account");
            message.setText("Open this verification link within 24 hours:\n"
                    + frontendBaseUrl + "/verify-email?token=" + token);
            mail.send(message);
            return new RegisterResponse(userId, true);
        } catch (DataIntegrityViolationException exception) {
            throw new AuthApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "This email is already registered");
        }
    }

    @Transactional
    public VerifyResponse verify(String token) {
        List<Long> userIds = jdbc.query("""
                UPDATE auth_tokens SET used_at = now()
                WHERE token_hash = ? AND purpose = 'EMAIL_VERIFICATION'
                  AND used_at IS NULL AND expires_at > now()
                RETURNING user_id
                """, (rs, row) -> rs.getLong("user_id"), hash(token));
        if (userIds.isEmpty()) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_TOKEN",
                    "Verification link is invalid or expired");
        }
        jdbc.update("""
                UPDATE users SET status = 'ACTIVE', verified_at = now(), updated_at = now()
                WHERE id = ? AND status = 'PENDING_VERIFICATION'
                """, userIds.getFirst());
        return new VerifyResponse(true);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        List<StoredUser> matches = jdbc.query("""
                SELECT id,email,password_hash,full_name,role,status,verified_at
                FROM users WHERE email = ?
                """, (rs, row) -> new StoredUser(rs.getLong("id"), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("full_name"),
                        rs.getString("role"), rs.getString("status"),
                        rs.getTimestamp("verified_at") != null), email);
        if (matches.isEmpty() || !passwords.matches(request.password(), matches.getFirst().passwordHash())
                || !"ACTIVE".equals(matches.getFirst().status()) || !matches.getFirst().verified()) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Invalid credentials or inactive account");
        }
        StoredUser user = matches.getFirst();
        String token = newToken();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?, ?, ?)",
                user.id(), hash(token), Timestamp.from(expiresAt));
        return new LoginResponse(token, expiresAt,
                new UserResponse(user.id(), user.email(), user.fullName(), user.role()));
    }

    @Transactional
    public void logout(long userId, String token) {
        jdbc.update("""
                UPDATE user_sessions SET revoked_at = now()
                WHERE user_id = ? AND token_hash = ? AND revoked_at IS NULL
                """, userId, hash(token));
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record StoredUser(long id, String email, String passwordHash, String fullName,
                              String role, String status, boolean verified) {}
}
