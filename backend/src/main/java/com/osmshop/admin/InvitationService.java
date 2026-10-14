package com.osmshop.admin;

import com.osmshop.admin.InvitationController.AcceptRequest;
import com.osmshop.admin.InvitationController.InvitationView;
import com.osmshop.admin.InvitationController.InviteRequest;
import com.osmshop.auth.AuthApiException;
import com.osmshop.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
public class InvitationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationService.class);
    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "MANAGER", "SUPPORT", "WAREHOUSE", "DELIVERY");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JavaMailSender mail;
    private final TransactionTemplate transactions;
    private final String mailFrom;
    private final String frontendBaseUrl;

    public InvitationService(JdbcTemplate jdbc, PasswordEncoder passwords, JavaMailSender mail,
                             TransactionTemplate transactions,
                             @Value("${app.mail-from}") String mailFrom,
                             @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.mail = mail;
        this.transactions = transactions;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public InvitationView invite(long adminId, InviteRequest request) {
        String role = request.role().trim().toUpperCase(Locale.ROOT);
        if (!STAFF_ROLES.contains(role)) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "INVALID_ROLE", "Vai trò nhân viên không hợp lệ.");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Long id = transactions.execute(status -> {
            Long existing = jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Long.class, email);
            if (existing != null && existing > 0) throw new AuthApiException(HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_REGISTERED", "Email đã có tài khoản.");
            jdbc.update("""
                    UPDATE staff_invitations SET revoked_at=now()
                    WHERE email=? AND accepted_at IS NULL AND revoked_at IS NULL
                    """, email);
            return jdbc.queryForObject("""
                    INSERT INTO staff_invitations(email,full_name,phone,role,token_hash,invited_by,expires_at,delivery_status)
                    VALUES (?,?,?,?,?,?,?,'PENDING') RETURNING id
                    """, Long.class, email, request.fullName().trim(), request.phone(), role,
                    AuthService.hash(token), adminId, Timestamp.from(Instant.now().plus(48, ChronoUnit.HOURS)));
        });
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("FORME - Lời mời tham gia đội ngũ");
        message.setText("Bạn được mời tham gia đội ngũ FORME với vai trò " + role
                + ".\nMở liên kết sau trong vòng 48 giờ để tự đặt mật khẩu:\n"
                + frontendBaseUrl + "/accept-invitation?token=" + token
                + "\n\nNếu bạn không mong đợi lời mời này, hãy bỏ qua email.");
        try {
            mail.send(message);
        } catch (MailException exception) {
            jdbc.update("""
                    UPDATE staff_invitations SET delivery_status='FAILED',revoked_at=now() WHERE id=?
                    """, id);
            LOGGER.warn("Invitation delivery failed for invitation id {} ({})", id,
                    exception.getClass().getSimpleName());
            throw new AuthApiException(HttpStatus.SERVICE_UNAVAILABLE, "INVITATION_DELIVERY_FAILED",
                    "Không gửi được email lời mời. Vui lòng kiểm tra SMTP rồi mời lại.");
        }
        transactions.executeWithoutResult(status -> {
            int changed = jdbc.update("""
                    UPDATE staff_invitations SET delivery_status='SENT'
                    WHERE id=? AND revoked_at IS NULL AND delivery_status='PENDING'
                    """, id);
            if (changed == 0) throw new AuthApiException(HttpStatus.CONFLICT, "INVITATION_REPLACED",
                    "Lời mời này đã được thay bằng lời mời mới.");
            jdbc.update("""
                    INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                    VALUES (?,'USER_INVITED','USER',?,jsonb_build_object('role',?))
                    """, adminId, email, role);
        });
        return view(id);
    }

    public List<InvitationView> list() {
        return jdbc.query("""
                SELECT id,email,full_name,role,expires_at,accepted_at,created_at,
                  CASE WHEN accepted_at IS NOT NULL THEN 'ACCEPTED'
                       WHEN delivery_status='FAILED' THEN 'FAILED'
                       WHEN revoked_at IS NOT NULL THEN 'REVOKED'
                       WHEN expires_at<=now() THEN 'EXPIRED'
                       WHEN delivery_status='PENDING' AND created_at<=now()-interval '5 minutes' THEN 'STALLED'
                       WHEN delivery_status='PENDING' THEN 'PENDING'
                       ELSE 'SENT' END AS status
                FROM staff_invitations ORDER BY id DESC LIMIT 100
                """, (rs, row) -> new InvitationView(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getObject(5, OffsetDateTime.class),
                rs.getObject(6, OffsetDateTime.class), rs.getObject(7, OffsetDateTime.class), rs.getString(8)));
    }

    public InvitationView resend(long adminId, long id) {
        InvitationDraft draft = jdbc.query("""
                SELECT email,full_name,phone,role FROM staff_invitations
                WHERE id=? AND accepted_at IS NULL
                  AND (revoked_at IS NULL OR delivery_status='FAILED')
                  AND (delivery_status='FAILED' OR expires_at<=now()
                       OR (delivery_status='PENDING' AND created_at<=now()-interval '5 minutes'))
                """, (rs, row) -> new InvitationDraft(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4)), id).stream().findFirst().orElseThrow(() ->
                new AuthApiException(HttpStatus.CONFLICT, "INVITATION_NOT_RESENDABLE",
                        "Chỉ gửi lại được lời mời thất bại, hết hạn hoặc kẹt trạng thái gửi."));
        return invite(adminId, new InviteRequest(draft.email(), draft.fullName(), draft.phone(), draft.role()));
    }

    @Transactional
    public InvitationView revoke(long adminId, long id) {
        int changed = jdbc.update("""
                UPDATE staff_invitations SET revoked_at=now()
                WHERE id=? AND accepted_at IS NULL AND revoked_at IS NULL
                """, id);
        if (changed == 0) throw new AuthApiException(HttpStatus.CONFLICT, "INVITATION_NOT_REVOCABLE",
                "Lời mời không còn hiệu lực để thu hồi.");
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                VALUES (?,'INVITATION_REVOKED','STAFF_INVITATION',?,'{}'::jsonb)
                """, adminId, Long.toString(id));
        return view(id);
    }

    @Transactional
    public void accept(AcceptRequest request) {
        if (!request.password().equals(request.confirmPassword())) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "PASSWORD_MISMATCH", "Mật khẩu nhập lại không khớp.");
        List<PendingInvitation> pending = jdbc.query("""
                SELECT id,email,full_name,phone,role,invited_by FROM staff_invitations
                WHERE token_hash=? AND accepted_at IS NULL AND revoked_at IS NULL
                  AND delivery_status='SENT' AND expires_at>now() FOR UPDATE
                """, (rs, row) -> new PendingInvitation(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getLong(6)), AuthService.hash(request.token()));
        if (pending.isEmpty()) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "INVALID_INVITATION", "Lời mời không hợp lệ hoặc đã hết hạn.");
        PendingInvitation invitation = pending.getFirst();
        try {
            Long userId = jdbc.queryForObject("""
                    INSERT INTO users(email,password_hash,full_name,phone,role,status,verified_at)
                    VALUES (?,?,?,?,?,'ACTIVE',now()) RETURNING id
                    """, Long.class, invitation.email(), passwords.encode(request.password()),
                    invitation.fullName(), invitation.phone(), invitation.role());
            jdbc.update("UPDATE staff_invitations SET accepted_at=now() WHERE id=?", invitation.id());
            jdbc.update("""
                    INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                    VALUES (?,'INVITATION_ACCEPTED','USER',?, '{}'::jsonb)
                    """, invitation.invitedBy(), Long.toString(userId));
        } catch (DataIntegrityViolationException exception) {
            throw new AuthApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email đã có tài khoản.");
        }
    }

    private InvitationView view(long id) {
        return jdbc.queryForObject("""
                SELECT id,email,full_name,role,expires_at,accepted_at,created_at,
                  CASE WHEN accepted_at IS NOT NULL THEN 'ACCEPTED'
                       WHEN delivery_status='FAILED' THEN 'FAILED'
                       WHEN revoked_at IS NOT NULL THEN 'REVOKED'
                       WHEN expires_at<=now() THEN 'EXPIRED'
                       WHEN delivery_status='PENDING' AND created_at<=now()-interval '5 minutes' THEN 'STALLED'
                       WHEN delivery_status='PENDING' THEN 'PENDING'
                       ELSE 'SENT' END AS status
                FROM staff_invitations WHERE id=?
                """, (rs, row) -> new InvitationView(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getObject(5, OffsetDateTime.class),
                rs.getObject(6, OffsetDateTime.class), rs.getObject(7, OffsetDateTime.class), rs.getString(8)), id);
    }

    private record PendingInvitation(long id, String email, String fullName, String phone,
                                     String role, long invitedBy) {}
    private record InvitationDraft(String email, String fullName, String phone, String role) {}
}
