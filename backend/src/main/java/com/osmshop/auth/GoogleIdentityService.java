package com.osmshop.auth;

import com.osmshop.auth.AuthDtos.LoginResponse;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleIdentityService {
    private final GoogleIdTokenVerifier verifier;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuthRateLimiter limits;
    private final AuthService auth;

    public GoogleIdentityService(GoogleIdTokenVerifier verifier, JdbcTemplate jdbc,
                                 PasswordEncoder passwords, AuthRateLimiter limits, AuthService auth) {
        this.verifier = verifier;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.limits = limits;
        this.auth = auth;
    }

    public boolean enabled() { return verifier.enabled(); }
    public String clientId() { return verifier.clientId(); }

    @Transactional
    public LoginResponse login(String credential, String ipAddress, String userAgent) {
        limits.check("google-login-ip", ipAddress, 100, 3600);
        GoogleIdTokenVerifier.GoogleIdentity identity = verifier.verify(credential);
        List<Long> linked = jdbc.query("SELECT user_id FROM google_identities WHERE google_sub=?",
                (rs, row) -> rs.getLong(1), identity.subject());
        long userId;
        if (!linked.isEmpty()) {
            userId = linked.getFirst();
        } else {
            Long existing = jdbc.query("SELECT id FROM users WHERE email=?",
                    (rs, row) -> rs.getLong(1), identity.email()).stream().findFirst().orElse(null);
            if (existing != null) throw new AuthApiException(HttpStatus.CONFLICT, "GOOGLE_LINK_REQUIRED",
                    "Email này đã có tài khoản. Hãy đăng nhập bằng mật khẩu rồi liên kết Google trong Bảo mật tài khoản.");
            // Google accounts never gain staff roles through public sign-in.
            List<Long> created = jdbc.query("""
                    INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                    VALUES (?,?,?,'CUSTOMER','ACTIVE',now())
                    ON CONFLICT (email) DO NOTHING RETURNING id
                    """, (rs, row) -> rs.getLong(1), identity.email(),
                    passwords.encode(java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID()),
                    identity.fullName());
            if (created.isEmpty()) {
                // A second browser request may have created this same Google account concurrently.
                List<Long> concurrentlyLinked = jdbc.query(
                        "SELECT user_id FROM google_identities WHERE google_sub=?",
                        (rs, row) -> rs.getLong(1), identity.subject());
                if (concurrentlyLinked.isEmpty()) throw new AuthApiException(HttpStatus.CONFLICT,
                        "GOOGLE_LINK_REQUIRED", "Email này đã có tài khoản. Hãy đăng nhập bằng mật khẩu rồi liên kết Google.");
                userId = concurrentlyLinked.getFirst();
            } else {
                userId = created.getFirst();
                jdbc.update("INSERT INTO google_identities(user_id,google_sub) VALUES (?,?)", userId,
                        identity.subject());
                jdbc.update("""
                        INSERT INTO audit_logs(actor_id,action,entity_type,entity_id)
                        VALUES (?,'AUTH_GOOGLE_REGISTER','USER',?)
                        """, userId, Long.toString(userId));
            }
        }
        return auth.loginGoogleUser(userId, ipAddress, userAgent);
    }

    public boolean isLinked(long userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM google_identities WHERE user_id=?",
                Integer.class, userId);
        return count != null && count > 0;
    }

    @Transactional
    public void link(long userId, String password, String credential) {
        limits.check("google-link", Long.toString(userId), 5, 900);
        GoogleIdTokenVerifier.GoogleIdentity identity = verifier.verify(credential);
        List<UserForLink> users = jdbc.query("""
                SELECT email,password_hash FROM users WHERE id=? AND status='ACTIVE' AND verified_at IS NOT NULL
                """, (rs, row) -> new UserForLink(rs.getString(1), rs.getString(2)), userId);
        if (users.isEmpty() || !passwords.matches(password, users.getFirst().passwordHash()))
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CURRENT_PASSWORD",
                    "Mật khẩu hiện tại không đúng.");
        if (!users.getFirst().email().equals(identity.email()))
            throw new AuthApiException(HttpStatus.CONFLICT, "GOOGLE_EMAIL_MISMATCH",
                    "Email Google phải trùng với email tài khoản FORME.");
        try {
            jdbc.update("INSERT INTO google_identities(user_id,google_sub) VALUES (?,?)", userId, identity.subject());
        } catch (DataIntegrityViolationException exception) {
            throw new AuthApiException(HttpStatus.CONFLICT, "GOOGLE_ALREADY_LINKED",
                    "Tài khoản Google này đã được liên kết.");
        }
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id)
                VALUES (?,'AUTH_GOOGLE_LINK','USER',?)
                """, userId, Long.toString(userId));
    }

    private record UserForLink(String email, String passwordHash) {}
}
