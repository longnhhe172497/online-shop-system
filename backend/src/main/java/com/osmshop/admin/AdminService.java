package com.osmshop.admin;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import com.osmshop.admin.AdminController.CreateUser;
import com.osmshop.admin.AdminController.UpdateUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final Set<String> ROLES = Set.of("CUSTOMER", "ADMIN", "MANAGER", "SUPPORT", "WAREHOUSE", "DELIVERY");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "LOCKED");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public AdminService(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    public PageResponse<UserView> users(String search, String role, String status, int page, int size) {
        validatePage(page, size);
        String filter = " WHERE (lower(email) LIKE ? OR lower(full_name) LIKE ?) AND (? = '' OR role = ?) AND (? = '' OR status = ?)";
        String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        Object[] args = {term, term, role, role, status, status};
        Long count = jdbc.queryForObject("SELECT count(*) FROM users" + filter, Long.class, args);
        List<UserView> items = jdbc.query("SELECT id,email,full_name,phone,role,status,created_at,verified_at FROM users"
                + filter + " ORDER BY id DESC LIMIT ? OFFSET ?", AdminService::mapUser,
                term, term, role, role, status, status, size, page * size);
        return new PageResponse<>(items, page, size, count == null ? 0 : count);
    }

    @Transactional
    public UserView create(long actorId, CreateUser request) {
        String role = checkedRole(request.role());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO users(email,password_hash,full_name,phone,role,status,verified_at)
                    VALUES (?,?,?,?,?,'ACTIVE',now()) RETURNING id
                    """, Long.class, email, passwords.encode(request.password()), request.fullName().trim(),
                    request.phone(), role);
            audit(actorId, "USER_CREATED", "USER", id.toString(), "role", role);
            return user(id);
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email already registered");
        }
    }

    @Transactional
    public UserView update(long actorId, long id, UpdateUser request) {
        if (jdbc.update("UPDATE users SET full_name=?,phone=?,updated_at=now() WHERE id=?",
                request.fullName().trim(), request.phone(), id) == 0) throw notFound();
        audit(actorId, "USER_UPDATED", "USER", Long.toString(id), "fields", "fullName,phone");
        return user(id);
    }

    @Transactional
    public UserView changeRole(long actorId, long id, String requestedRole) {
        String next = checkedRole(requestedRole);
        UserView previous = lockUser(id);
        if ("ADMIN".equals(previous.role()) && !"ADMIN".equals(next)) protectLastAdmin();
        jdbc.update("UPDATE users SET role=?,updated_at=now() WHERE id=?", next, id);
        if (!previous.role().equals(next)) jdbc.update("""
                UPDATE user_sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL
                """, id);
        audit(actorId, "USER_ROLE_CHANGED", "USER", Long.toString(id), "role", next);
        return user(id);
    }

    @Transactional
    public UserView changeStatus(long actorId, long id, String requestedStatus) {
        String next = requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(next)) throw error(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Invalid status");
        UserView previous = lockUser(id);
        if ("ADMIN".equals(previous.role()) && "ACTIVE".equals(previous.status()) && !"ACTIVE".equals(next))
            protectLastAdmin();
        if ("ACTIVE".equals(next) && previous.verifiedAt() == null)
            throw error(HttpStatus.CONFLICT, "EMAIL_NOT_VERIFIED", "Email not verified");
        jdbc.update("UPDATE users SET status=?,updated_at=now() WHERE id=?", next, id);
        if (!previous.status().equals(next)) jdbc.update("""
                UPDATE user_sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL
                """, id);
        audit(actorId, "USER_STATUS_CHANGED", "USER", Long.toString(id), "status", next);
        return user(id);
    }

    public List<SettingView> settings() {
        return jdbc.query("SELECT setting_key,value_text,description,updated_at FROM system_settings ORDER BY setting_key",
                (rs, row) -> new SettingView(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class)));
    }

    @Transactional
    public SettingView changeSetting(long actorId, String key, String value) {
        String next = validateSetting(key, value);
        if (jdbc.update("UPDATE system_settings SET value_text=?,updated_by=?,updated_at=now() WHERE setting_key=?",
                next, actorId, key) == 0) throw notFound();
        audit(actorId, "SETTING_CHANGED", "SYSTEM_SETTING", key, "value", next);
        return jdbc.queryForObject("SELECT setting_key,value_text,description,updated_at FROM system_settings WHERE setting_key=?",
                (rs, row) -> new SettingView(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class)), key);
    }

    public PageResponse<AuditView> audit(int page, int size) {
        validatePage(page, size);
        Long count = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        List<AuditView> items = jdbc.query("""
                SELECT a.id,a.actor_id,u.email,a.action,a.entity_type,a.entity_id,a.created_at
                FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_id
                ORDER BY a.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new AuditView(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class)),
                size, page * size);
        return new PageResponse<>(items, page, size, count == null ? 0 : count);
    }

    private UserView user(long id) {
        return jdbc.query("SELECT id,email,full_name,phone,role,status,created_at,verified_at FROM users WHERE id=?",
                AdminService::mapUser, id).stream().findFirst().orElseThrow(this::notFound);
    }

    private UserView lockUser(long id) {
        return jdbc.query("SELECT id,email,full_name,phone,role,status,created_at,verified_at FROM users WHERE id=? FOR UPDATE",
                AdminService::mapUser, id).stream().findFirst().orElseThrow(this::notFound);
    }

    private void protectLastAdmin() {
        jdbc.execute("SELECT pg_advisory_xact_lock(490001)");
        Long count = jdbc.queryForObject("SELECT count(*) FROM users WHERE role='ADMIN' AND status='ACTIVE'", Long.class);
        if (count != null && count <= 1) throw error(HttpStatus.CONFLICT, "LAST_ADMIN", "Cannot remove last active admin");
    }

    private static UserView mapUser(ResultSet rs, int row) throws SQLException {
        return new UserView(rs.getLong("id"), rs.getString("email"), rs.getString("full_name"),
                rs.getString("phone"), rs.getString("role"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("verified_at", OffsetDateTime.class));
    }

    private void audit(long actorId, String action, String type, String id, String field, String value) {
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                VALUES (?,?,?,?,jsonb_build_object(?,?))
                """, actorId, action, type, id, field, value);
    }

    private static String checkedRole(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(normalized)) throw error(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Invalid role");
        return normalized;
    }

    private static String validateSetting(String key, String value) {
        if ("reporting_time_zone".equals(key)) {
            if (!"Asia/Ho_Chi_Minh".equals(value)) throw error(HttpStatus.BAD_REQUEST, "INVALID_SETTING", "Invalid time zone");
            return value;
        }
        int max = switch (key) {
            case "qr_expiry_minutes" -> 60;
            case "max_qr_attempts", "max_delivery_attempts" -> 10;
            case "return_window_days" -> 30;
            case "max_saved_addresses" -> 20;
            default -> throw error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Setting not found");
        };
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > max) throw new NumberFormatException();
            return Integer.toString(parsed);
        } catch (NumberFormatException exception) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_SETTING", "Setting out of allowed range");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > 1_000_000)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Invalid page or size");
    }

    private AuthApiException notFound() { return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"); }
    private static AuthApiException error(HttpStatus status, String code, String detail) {
        return new AuthApiException(status, code, detail);
    }

    public record UserView(long id, String email, String fullName, String phone, String role, String status,
            OffsetDateTime createdAt, OffsetDateTime verifiedAt) {}
    public record SettingView(String key, String value, String description, OffsetDateTime updatedAt) {}
    public record AuditView(long id, Long actorId, String actorEmail, String action, String entityType,
            String entityId, OffsetDateTime createdAt) {}
}
