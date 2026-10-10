package com.osmshop.auth;

import com.osmshop.auth.AuthDtos.LoginRequest;
import com.osmshop.auth.AuthDtos.LoginResponse;
import com.osmshop.auth.AuthDtos.PasswordResetConfirmRequest;
import com.osmshop.auth.AuthDtos.PasswordResetConfirmResponse;
import com.osmshop.auth.AuthDtos.RegisterRequest;
import com.osmshop.auth.AuthDtos.RegisterResponse;
import com.osmshop.auth.AuthDtos.UserResponse;
import com.osmshop.auth.AuthDtos.VerifyResponse;
import com.osmshop.auth.AuthDtos.SessionView;
import com.osmshop.auth.AuthDtos.MfaStepUpResponse;
import com.osmshop.auth.AuthDtos.TotpStartResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JavaMailSender mail;
    private final String mailFrom;
    private final String frontendBaseUrl;
    private final AuthRateLimiter limits;
    private final TransactionTemplate transactions;
    private final TotpService totp;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords, JavaMailSender mail,
                       AuthRateLimiter limits, TransactionTemplate transactions, TotpService totp,
                       @Value("${app.mail-from}") String mailFrom,
                       @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.mail = mail;
        this.limits = limits;
        this.transactions = transactions;
        this.totp = totp;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public RegisterResponse register(RegisterRequest request, String ipAddress) {
        limits.check("register", ipAddress, 30, 3600);
        if (!request.password().equals(request.confirmPassword())) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH",
                    "Mật khẩu nhập lại không khớp.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String token = newToken();
        Long userId;
        try {
            userId = transactions.execute(status -> {
                Long created = jdbc.queryForObject("""
                        INSERT INTO users(email,password_hash,full_name,phone,role,status)
                        VALUES (?, ?, ?, ?, 'CUSTOMER', 'PENDING_VERIFICATION') RETURNING id
                        """, Long.class, email, passwords.encode(request.password()),
                        request.fullName().trim(), request.phone());
                jdbc.update("""
                        INSERT INTO auth_tokens(user_id,token_hash,purpose,expires_at)
                        VALUES (?, ?, 'EMAIL_VERIFICATION', ?)
                        """, created, hash(token), Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
                return created;
            });
        } catch (DataIntegrityViolationException exception) {
            throw new AuthApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "Email này đã có tài khoản.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("FORME - Xác minh địa chỉ email");
        message.setText("Chào mừng bạn đến với FORME.\n\nMở liên kết sau trong vòng 24 giờ để xác minh email:\n"
                + frontendBaseUrl + "/verify-email?token=" + token
                + "\n\nNếu bạn không đăng ký, vui lòng bỏ qua email này.");
        try {
            mail.send(message);
            return new RegisterResponse(userId, true, true);
        } catch (MailException exception) {
            jdbc.update("UPDATE auth_tokens SET used_at=now() WHERE token_hash=?", hash(token));
            LOGGER.warn("Registration email delivery failed for user id {} ({})", userId,
                    exception.getClass().getSimpleName());
            return new RegisterResponse(userId, true, false);
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
                    "Liên kết xác minh không hợp lệ hoặc đã hết hạn.");
        }
        int activated = jdbc.update("""
                UPDATE users SET status = 'ACTIVE', verified_at = now(), updated_at = now()
                WHERE id = ? AND status = 'PENDING_VERIFICATION'
                """, userIds.getFirst());
        if (activated == 0) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "INVALID_VERIFICATION_TOKEN", "Liên kết xác minh không còn hiệu lực.");
        return new VerifyResponse(true);
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
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
            limits.check("login-email", email, 10, 900);
            limits.check("login-ip", ipAddress, 100, 900);
            jdbc.update("""
                    INSERT INTO audit_logs(action,entity_type,entity_id,details)
                    VALUES ('AUTH_LOGIN_FAILED','USER',?,jsonb_build_object('ip',?))
                    """, matches.isEmpty() ? null : Long.toString(matches.getFirst().id()),
                    safeLength(ipAddress, 64));
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Email hoặc mật khẩu không đúng, hoặc tài khoản chưa thể đăng nhập.");
        }
        StoredUser user = matches.getFirst();
        Boolean mfaEnabled = jdbc.queryForObject("SELECT mfa_enabled FROM users WHERE id=?", Boolean.class, user.id());
        if (Boolean.TRUE.equals(mfaEnabled)) {
            String method = mfaMethod(user.id());
            String challenge = "TOTP".equals(method) ? createTotpChallenge(user.id(), "LOGIN")
                    : createMfaChallenge(user.id(), email, "LOGIN");
            return new LoginResponse(null, null, null, true, challenge, method);
        }
        return createSession(user, ipAddress, userAgent);
    }

    private LoginResponse createSession(StoredUser user, String ipAddress, String userAgent) {
        String token = newToken();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        jdbc.update("""
                INSERT INTO user_sessions(user_id,token_hash,expires_at,ip_address,user_agent,last_seen_at)
                VALUES (?, ?, ?, ?, ?, now())
                """, user.id(), hash(token), Timestamp.from(expiresAt),
                safeLength(ipAddress, 64), safeLength(userAgent, 300));
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                VALUES (?,'AUTH_LOGIN_SUCCESS','USER',?,jsonb_build_object('ip',?))
                """, user.id(), Long.toString(user.id()), safeLength(ipAddress, 64));
        return new LoginResponse(token, expiresAt,
                new UserResponse(user.id(), user.email(), user.fullName(), user.role()), false, null, null);
    }

    public void requestPasswordReset(String requestedEmail, String ipAddress) {
        limits.check("reset-ip", ipAddress, 30, 3600);
        String email = requestedEmail.trim().toLowerCase(Locale.ROOT);
        limits.check("reset-email", email, 5, 3600);
        List<Long> userIds = jdbc.query("""
                SELECT id FROM users
                WHERE email = ? AND status = 'ACTIVE' AND verified_at IS NOT NULL
                """, (rs, row) -> rs.getLong("id"), email);
        if (userIds.isEmpty()) {
            return;
        }
        long userId = userIds.getFirst();
        String token = newToken();
        Boolean issued = transactions.execute(status -> {
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, userId);
            Long recentTokens = jdbc.queryForObject("""
                    SELECT count(*) FROM auth_tokens
                    WHERE user_id = ? AND purpose = 'PASSWORD_RESET'
                      AND used_at IS NULL AND created_at > now() - interval '5 minutes'
                    """, Long.class, userId);
            if (recentTokens != null && recentTokens > 0) return false;
            jdbc.update("""
                    INSERT INTO auth_tokens(user_id,token_hash,purpose,expires_at)
                    VALUES (?, ?, 'PASSWORD_RESET', ?)
                    """, userId, hash(token), Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
            return true;
        });
        if (!Boolean.TRUE.equals(issued)) return;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("FORME - Đặt lại mật khẩu");
        message.setText("Bạn đã yêu cầu đặt lại mật khẩu FORME. Liên kết có hiệu lực trong 30 phút:\n"
                + frontendBaseUrl + "/reset-password?token=" + token
                + "\n\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        try {
            mail.send(message);
        } catch (MailException exception) {
            // Keep the response identical for known and unknown accounts.
            jdbc.update("UPDATE auth_tokens SET used_at=now() WHERE token_hash=?", hash(token));
            LOGGER.warn("Password reset email delivery failed for user id {} ({})",
                    userId, exception.getClass().getSimpleName());
        }
    }

    @Transactional
    public PasswordResetConfirmResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH",
                    "Mật khẩu nhập lại không khớp.");
        }
        List<Long> userIds = jdbc.query("""
                UPDATE auth_tokens SET used_at = now()
                WHERE token_hash = ? AND purpose = 'PASSWORD_RESET'
                  AND used_at IS NULL AND expires_at > now()
                  AND EXISTS (SELECT 1 FROM users u WHERE u.id = auth_tokens.user_id
                              AND u.status = 'ACTIVE' AND u.verified_at IS NOT NULL)
                RETURNING user_id
                """, (rs, row) -> rs.getLong("user_id"), hash(request.token()));
        if (userIds.isEmpty()) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                    "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }
        long userId = userIds.getFirst();
        jdbc.update("UPDATE users SET password_hash = ?, updated_at = now() WHERE id = ?",
                passwords.encode(request.newPassword()), userId);
        jdbc.update("UPDATE user_sessions SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL",
                userId);
        invalidatePendingSecurityActions(userId);
        jdbc.update("""
                UPDATE auth_tokens SET used_at = now()
                WHERE user_id = ? AND purpose = 'PASSWORD_RESET' AND used_at IS NULL
                """, userId);
        return new PasswordResetConfirmResponse(true);
    }

    @Transactional
    public void logout(long userId, String token) {
        jdbc.update("""
                UPDATE user_sessions SET revoked_at = now()
                WHERE user_id = ? AND token_hash = ? AND revoked_at IS NULL
                """, userId, hash(token));
    }

    public void resendVerification(String requestedEmail, String ipAddress) {
        limits.check("verification-ip", ipAddress, 30, 3600);
        String email = requestedEmail.trim().toLowerCase(Locale.ROOT);
        limits.check("verification-email", email, 5, 3600);
        List<Long> users = jdbc.query("""
                SELECT id FROM users WHERE email=? AND status='PENDING_VERIFICATION'
                """, (rs, row) -> rs.getLong(1), email);
        if (users.isEmpty()) return;
        String token = newToken();
        Boolean issued = transactions.execute(status -> {
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, users.getFirst());
            Long recent = jdbc.queryForObject("""
                    SELECT count(*) FROM auth_tokens WHERE user_id=? AND purpose='EMAIL_VERIFICATION'
                      AND used_at IS NULL AND created_at > now() - interval '5 minutes'
                    """, Long.class, users.getFirst());
            if (recent != null && recent > 0) return false;
            jdbc.update("""
                    INSERT INTO auth_tokens(user_id,token_hash,purpose,expires_at)
                    VALUES (?,?,'EMAIL_VERIFICATION',?)
                    """, users.getFirst(), hash(token), Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
            return true;
        });
        if (!Boolean.TRUE.equals(issued)) return;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("FORME - Liên kết xác minh mới");
        message.setText("Mở liên kết sau trong vòng 24 giờ để xác minh tài khoản FORME:\n"
                + frontendBaseUrl + "/verify-email?token=" + token
                + "\n\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        try { mail.send(message); }
        catch (MailException exception) {
            jdbc.update("UPDATE auth_tokens SET used_at=now() WHERE token_hash=?", hash(token));
            LOGGER.warn("Verification resend failed for user id {} ({})", users.getFirst(),
                    exception.getClass().getSimpleName());
        }
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword,
                               String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Mật khẩu nhập lại không khớp.");
        }
        String currentHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, userId);
        if (!passwords.matches(currentPassword, currentHash)) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Mật khẩu hiện tại không đúng.");
        }
        jdbc.update("UPDATE users SET password_hash=?,updated_at=now() WHERE id=?", passwords.encode(newPassword), userId);
        jdbc.update("UPDATE user_sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL", userId);
        invalidatePendingSecurityActions(userId);
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                VALUES (?,'PASSWORD_CHANGED','USER',?, '{}'::jsonb)
                """, userId, Long.toString(userId));
    }

    public List<SessionView> sessions(long userId, String currentToken) {
        String currentHash = hash(currentToken);
        return jdbc.query("""
                SELECT id,ip_address,user_agent,created_at,last_seen_at,expires_at,token_hash
                FROM user_sessions WHERE user_id=? AND revoked_at IS NULL AND expires_at > now()
                  AND coalesce(last_seen_at,created_at)>now()-interval '30 minutes'
                ORDER BY created_at DESC
                """, (rs, row) -> new SessionView(rs.getLong("id"), rs.getString("ip_address"),
                rs.getString("user_agent"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("last_seen_at", OffsetDateTime.class) == null ? null :
                        rs.getObject("last_seen_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                currentHash.equals(rs.getString("token_hash"))), userId);
    }

    @Transactional
    public void revokeSession(long userId, long sessionId) {
        jdbc.update("UPDATE user_sessions SET revoked_at=now() WHERE id=? AND user_id=? AND revoked_at IS NULL",
                sessionId, userId);
    }

    @Transactional
    public void revokeOtherSessions(long userId, String currentToken) {
        jdbc.update("""
                UPDATE user_sessions SET revoked_at=now()
                WHERE user_id=? AND token_hash<>? AND revoked_at IS NULL
                """, userId, hash(currentToken));
    }

    @Transactional
    public String startMfaEnrollment(long userId) {
        Boolean enabled = jdbc.queryForObject("SELECT mfa_enabled FROM users WHERE id=?", Boolean.class, userId);
        if (Boolean.TRUE.equals(enabled)) {
            throw new AuthApiException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED", "Xác minh hai bước đã được bật.");
        }
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, userId);
        return createMfaChallenge(userId, email, "ENROLL");
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public List<String> finishMfaEnrollment(long userId, String challengeToken, String code) {
        if (mfaEnabled(userId)) throw new AuthApiException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED",
                "Xác minh hai bước đã được bật.");
        consumeMfaChallenge(challengeToken, code, "ENROLL", userId);
        jdbc.update("UPDATE users SET mfa_enabled=true,mfa_method='EMAIL',updated_at=now() WHERE id=?", userId);
        List<String> codes = issueRecoveryCodes(userId);
        auditMfa(userId, "MFA_EMAIL_ENABLED");
        notifyMfaChange(userId, "Đã bật mã xác minh qua email");
        return codes;
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public LoginResponse verifyLoginMfa(String challengeToken, String code, String ipAddress, String userAgent) {
        long userId = consumeMfaChallenge(challengeToken, code, "LOGIN", null);
        StoredUser user = jdbc.queryForObject("""
                SELECT id,email,password_hash,full_name,role,status,verified_at FROM users WHERE id=?
                """, (rs, row) -> new StoredUser(rs.getLong("id"), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("full_name"), rs.getString("role"),
                rs.getString("status"), rs.getTimestamp("verified_at") != null), userId);
        if (user == null || !"ACTIVE".equals(user.status()) || !user.verified()) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Tài khoản không khả dụng.");
        }
        return createSession(user, ipAddress, userAgent);
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public void disableMfa(long userId, String password, String challengeToken, String code) {
        requireCurrentPassword(userId, password);
        consumeMfaChallenge(challengeToken, code, "STEP_UP", userId);
        jdbc.update("UPDATE users SET mfa_enabled=false,updated_at=now() WHERE id=?", userId);
        jdbc.update("UPDATE email_mfa_challenges SET used_at=now() WHERE user_id=? AND used_at IS NULL", userId);
        jdbc.update("UPDATE mfa_recovery_codes SET used_at=now() WHERE user_id=? AND used_at IS NULL", userId);
        jdbc.update("DELETE FROM totp_credentials WHERE user_id=?", userId);
        auditMfa(userId, "MFA_DISABLED");
        notifyMfaChange(userId, "Đã tắt xác minh hai bước");
    }

    public boolean mfaEnabled(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT mfa_enabled FROM users WHERE id=?", Boolean.class, userId));
    }

    public String mfaMethod(long userId) {
        return jdbc.queryForObject("SELECT mfa_method FROM users WHERE id=?", String.class, userId);
    }

    public int recoveryCodesRemaining(long userId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM mfa_recovery_codes WHERE user_id=? AND used_at IS NULL
                """, Integer.class, userId);
        return count == null ? 0 : count;
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public List<String> regenerateRecoveryCodes(long userId, String password, String challengeToken, String code) {
        requireCurrentPassword(userId, password);
        if (!mfaEnabled(userId)) throw new AuthApiException(HttpStatus.CONFLICT, "MFA_NOT_ENABLED",
                "Xác minh hai bước chưa được bật.");
        consumeMfaChallenge(challengeToken, code, "STEP_UP", userId);
        jdbc.update("UPDATE mfa_recovery_codes SET used_at=now() WHERE user_id=? AND used_at IS NULL", userId);
        List<String> codes = issueRecoveryCodes(userId);
        auditMfa(userId, "MFA_RECOVERY_REGENERATED");
        notifyMfaChange(userId, "Đã thay mã khôi phục xác minh hai bước");
        return codes;
    }

    @Transactional
    public MfaStepUpResponse startMfaStepUp(long userId, String password) {
        requireCurrentPassword(userId, password);
        if (!mfaEnabled(userId)) throw new AuthApiException(HttpStatus.CONFLICT, "MFA_NOT_ENABLED",
                "Xác minh hai bước chưa được bật.");
        String method = mfaMethod(userId);
        String challenge = "TOTP".equals(method) ? createTotpChallenge(userId, "STEP_UP")
                : createMfaChallenge(userId, userEmail(userId), "STEP_UP");
        return new MfaStepUpResponse(challenge, method);
    }

    @Transactional
    public TotpStartResponse startTotpEnrollment(long userId, String password) {
        requireCurrentPassword(userId, password);
        if (mfaEnabled(userId)) throw new AuthApiException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED",
                "Hãy tắt phương thức xác minh hiện tại trước khi bật ứng dụng xác thực.");
        String secret = totp.newSecret();
        jdbc.update("""
                INSERT INTO totp_credentials(user_id,pending_secret,pending_expires_at)
                VALUES (?,?,now()+interval '10 minutes')
                ON CONFLICT (user_id) DO UPDATE SET pending_secret=excluded.pending_secret,
                    pending_expires_at=excluded.pending_expires_at,updated_at=now()
                """, userId, totp.encrypt(secret));
        return new TotpStartResponse(secret, totp.otpauthUri(userEmail(userId), secret));
    }

    @Transactional
    public List<String> confirmTotpEnrollment(long userId, String code) {
        limits.check("totp-enroll", Long.toString(userId), 10, 900);
        List<String> pending = jdbc.query("""
                SELECT pending_secret FROM totp_credentials
                WHERE user_id=? AND pending_secret IS NOT NULL AND pending_expires_at>now() FOR UPDATE
                """, (rs, row) -> rs.getString(1), userId);
        if (pending.isEmpty() || mfaEnabled(userId)) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "INVALID_TOTP_ENROLLMENT", "Thiết lập ứng dụng xác thực đã hết hạn. Hãy bắt đầu lại.");
        String secret = totp.decrypt(pending.getFirst());
        long step = totp.validStep(secret, code, Instant.now(), null);
        if (step < 0) throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_MFA_CODE",
                "Mã xác minh không đúng.");
        jdbc.update("""
                UPDATE totp_credentials SET encrypted_secret=pending_secret,pending_secret=NULL,
                    pending_expires_at=NULL,last_used_step=?,updated_at=now() WHERE user_id=?
                """, step, userId);
        jdbc.update("UPDATE users SET mfa_enabled=true,mfa_method='TOTP',updated_at=now() WHERE id=?", userId);
        List<String> codes = issueRecoveryCodes(userId);
        auditMfa(userId, "MFA_TOTP_ENABLED");
        notifyMfaChange(userId, "Đã bật xác minh bằng ứng dụng xác thực");
        return codes;
    }

    private void requireCurrentPassword(long userId, String password) {
        String currentHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=? FOR UPDATE",
                String.class, userId);
        if (!passwords.matches(password, currentHash)) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "INVALID_CURRENT_PASSWORD", "Mật khẩu hiện tại không đúng.");
    }

    private String userEmail(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, userId);
    }

    private void auditMfa(long userId, String action) {
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                VALUES (?,?,'USER',?,'{}'::jsonb)
                """, userId, action, Long.toString(userId));
    }

    private void notifyMfaChange(long userId, String change) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(userEmail(userId));
        message.setSubject("FORME - Thay đổi bảo mật tài khoản");
        message.setText(change + ". Nếu không phải bạn thực hiện, hãy đổi mật khẩu và liên hệ quản trị viên.");
        try { mail.send(message); }
        catch (MailException exception) {
            LOGGER.warn("MFA change notification failed for user id {} ({})", userId,
                    exception.getClass().getSimpleName());
        }
    }

    private List<String> issueRecoveryCodes(long userId) {
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            byte[] random = new byte[16];
            RANDOM.nextBytes(random);
            String raw = HexFormat.of().withUpperCase().formatHex(random);
            jdbc.update("INSERT INTO mfa_recovery_codes(user_id,code_hash) VALUES (?,?)",
                    userId, hash(raw));
            codes.add(raw.substring(0, 8) + "-" + raw.substring(8, 16) + "-"
                    + raw.substring(16, 24) + "-" + raw.substring(24));
        }
        return codes;
    }

    private String createMfaChallenge(long userId, String email, String purpose) {
        String challenge = newToken();
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        jdbc.update("""
                INSERT INTO email_mfa_challenges(user_id,challenge_hash,code_hash,purpose,expires_at)
                VALUES (?,?,?,?,?)
                """, userId, hash(challenge), hash(challenge + ":" + code), purpose,
                Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("FORME - Mã xác minh hai bước");
        message.setText("Mã xác minh FORME của bạn: " + code
                + "\nMã có hiệu lực trong 10 phút. Không chia sẻ mã này cho người khác.");
        mail.send(message);
        return challenge;
    }

    private String createTotpChallenge(long userId, String purpose) {
        String challenge = newToken();
        jdbc.update("""
                INSERT INTO email_mfa_challenges(user_id,challenge_hash,code_hash,purpose,expires_at)
                VALUES (?,?,?,?,?)
                """, userId, hash(challenge), hash(challenge + ":totp"), purpose,
                Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        return challenge;
    }

    private long consumeMfaChallenge(String challengeToken, String code, String purpose, Long ownerId) {
        limits.check("mfa-challenge", challengeToken, 8, 900);
        List<MfaChallenge> matches = jdbc.query("""
                SELECT user_id,code_hash,attempts FROM email_mfa_challenges
                WHERE challenge_hash=? AND purpose=? AND used_at IS NULL AND expires_at>now()
                FOR UPDATE
                """, (rs, row) -> new MfaChallenge(rs.getLong(1), rs.getString(2), rs.getInt(3)),
                hash(challengeToken), purpose);
        if (matches.isEmpty() || (ownerId != null && matches.getFirst().userId() != ownerId)) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_MFA_CHALLENGE", "Mã xác minh không hợp lệ hoặc đã hết hạn.");
        }
        MfaChallenge match = matches.getFirst();
        String method = "ENROLL".equals(purpose) ? "EMAIL" : mfaMethod(match.userId());
        boolean emailCodeValid = "EMAIL".equals(method) && MessageDigest.isEqual(
                match.codeHash().getBytes(StandardCharsets.UTF_8),
                hash(challengeToken + ":" + code).getBytes(StandardCharsets.UTF_8));
        boolean recoveryCodeValid = false;
        if (match.attempts() < 5 && !emailCodeValid
                && ("LOGIN".equals(purpose) || "STEP_UP".equals(purpose))) {
            String normalized = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
            if (normalized.matches("[0-9A-F]{32}")) {
                List<Long> used = jdbc.query("""
                        UPDATE mfa_recovery_codes SET used_at=now()
                        WHERE user_id=? AND code_hash=? AND used_at IS NULL RETURNING id
                        """, (rs, row) -> rs.getLong(1), match.userId(), hash(normalized));
                recoveryCodeValid = !used.isEmpty();
            }
        }
        boolean totpCodeValid = false;
        if (match.attempts() < 5 && !recoveryCodeValid && "TOTP".equals(method)
                && code.matches("[0-9]{6}")) {
            List<TotpCredential> credentials = jdbc.query("""
                    SELECT encrypted_secret,last_used_step FROM totp_credentials WHERE user_id=? FOR UPDATE
                    """, (rs, row) -> new TotpCredential(rs.getString(1),
                    rs.getObject(2) == null ? null : rs.getLong(2)), match.userId());
            if (!credentials.isEmpty() && credentials.getFirst().encryptedSecret() != null) {
                long step = totp.validStep(totp.decrypt(credentials.getFirst().encryptedSecret()), code,
                        Instant.now(), credentials.getFirst().lastUsedStep());
                if (step >= 0) {
                    jdbc.update("UPDATE totp_credentials SET last_used_step=? WHERE user_id=?", step, match.userId());
                    totpCodeValid = true;
                }
            }
        }
        if (match.attempts() >= 5 || (!emailCodeValid && !totpCodeValid && !recoveryCodeValid)) {
            jdbc.update("UPDATE email_mfa_challenges SET attempts=attempts+1 WHERE challenge_hash=?", hash(challengeToken));
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_MFA_CODE", "Mã xác minh không đúng.");
        }
        jdbc.update("UPDATE email_mfa_challenges SET used_at=now() WHERE challenge_hash=?", hash(challengeToken));
        return match.userId();
    }

    private static String safeLength(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }

    private void invalidatePendingSecurityActions(long userId) {
        jdbc.update("UPDATE email_change_requests SET used_at=now() WHERE user_id=? AND used_at IS NULL", userId);
        jdbc.update("UPDATE email_mfa_challenges SET used_at=now() WHERE user_id=? AND used_at IS NULL", userId);
    }

    private record MfaChallenge(long userId, String codeHash, int attempts) {}
    private record TotpCredential(String encryptedSecret, Long lastUsedStep) {}

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
