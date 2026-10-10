package com.osmshop.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmailChangeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailChangeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JavaMailSender mail;
    private final TransactionTemplate transactions;
    private final AuthRateLimiter limits;
    private final String mailFrom;
    private final String frontendBaseUrl;

    public EmailChangeService(JdbcTemplate jdbc, PasswordEncoder passwords, JavaMailSender mail,
                              TransactionTemplate transactions, AuthRateLimiter limits,
                              @Value("${app.mail-from}") String mailFrom,
                              @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.jdbc = jdbc; this.passwords = passwords; this.mail = mail;
        this.transactions = transactions; this.limits = limits; this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public void request(long userId, String newEmailValue, String password) {
        limits.check("email-change-user", Long.toString(userId), 5, 86400);
        String newEmail = newEmailValue.trim().toLowerCase(Locale.ROOT);
        String currentHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, userId);
        if (!passwords.matches(password, currentHash)) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "INVALID_CURRENT_PASSWORD", "Mật khẩu hiện tại không đúng.");
        Long existing = jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Long.class, newEmail);
        if (existing != null && existing > 0) throw new AuthApiException(HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED", "Email này đã có tài khoản.");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE email_change_requests SET used_at=now() WHERE user_id=? AND used_at IS NULL", userId);
            jdbc.update("""
                    INSERT INTO email_change_requests(user_id,new_email,token_hash,expires_at)
                    VALUES (?,?,?,?)
                    """, userId, newEmail, AuthService.hash(token),
                    Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
        });
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom); message.setTo(newEmail);
        message.setSubject("FORME - Xác nhận địa chỉ email mới");
        message.setText("Mở liên kết sau trong vòng 30 phút để xác nhận email đăng nhập mới:\n"
                + frontendBaseUrl + "/email-change/confirm?token=" + token
                + "\n\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        try { mail.send(message); }
        catch (MailException exception) {
            jdbc.update("UPDATE email_change_requests SET used_at=now() WHERE token_hash=?", AuthService.hash(token));
            LOGGER.warn("Email change delivery failed for user id {} ({})", userId,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    @Transactional
    public void confirm(String token) {
        List<PendingChange> requests = jdbc.query("""
                SELECT id,user_id,new_email FROM email_change_requests
                WHERE token_hash=? AND used_at IS NULL AND expires_at>now() FOR UPDATE
                """, (rs, row) -> new PendingChange(rs.getLong(1), rs.getLong(2), rs.getString(3)),
                AuthService.hash(token));
        if (requests.isEmpty()) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "INVALID_EMAIL_CHANGE_TOKEN", "Liên kết đổi email không hợp lệ hoặc đã hết hạn.");
        PendingChange change = requests.getFirst();
        try {
            jdbc.update("UPDATE users SET email=?,verified_at=now(),updated_at=now() WHERE id=?",
                    change.newEmail(), change.userId());
            jdbc.update("UPDATE email_change_requests SET used_at=now() WHERE user_id=? AND used_at IS NULL",
                    change.userId());
            jdbc.update("UPDATE user_sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL",
                    change.userId());
            jdbc.update("""
                    INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                    VALUES (?,'EMAIL_CHANGED','USER',?, '{}'::jsonb)
                    """, change.userId(), Long.toString(change.userId()));
        } catch (DataIntegrityViolationException exception) {
            throw new AuthApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email này đã có tài khoản.");
        }
    }

    private record PendingChange(long id, long userId, String newEmail) {}
}
