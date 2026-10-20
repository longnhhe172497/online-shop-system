package com.osmshop.order;

import static com.osmshop.order.CheckoutRules.error;

import com.osmshop.api.PageResponse;
import com.osmshop.order.CheckoutRules.Voucher;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Voucher rules for checkout and Manager voucher maintenance. */
@Service
public class VoucherService {
    private static final String RULE_COLUMNS = """
            SELECT id,code,discount_type,discount_value,max_discount,min_order_amount,usage_limit,
                   status = 'ACTIVE' AND valid_from <= now() AND valid_until > now()
            FROM vouchers
            """;
    private static final String VIEW_COLUMNS = """
            SELECT v.id,v.code,v.discount_type,v.discount_value,v.max_discount,v.min_order_amount,v.usage_limit,
                   (SELECT count(*) FROM voucher_usages u WHERE u.voucher_id = v.id
                     AND u.status IN ('RESERVED','CONSUMED')),
                   v.valid_from,v.valid_until,v.status,v.created_at
            FROM vouchers v
            """;
    private final JdbcTemplate jdbc;

    public VoucherService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Checkout: finds a voucher by code (case-insensitive) and checks BR-V1…V3. */
    Voucher usableByCode(String code, BigDecimal subtotal, boolean lock) {
        Voucher voucher = jdbc.query(RULE_COLUMNS + " WHERE upper(code) = ?" + (lock ? " FOR UPDATE" : ""),
                        (rs, row) -> rule(rs), code.trim().toUpperCase(Locale.ROOT))
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.UNPROCESSABLE_CONTENT, "VOUCHER_INVALID", "Voucher does not exist"));
        return check(voucher, subtotal);
    }

    /** Payment retry: re-checks the voucher already attached to the order. */
    Voucher usableById(long id, BigDecimal subtotal) {
        Voucher voucher = jdbc.query(RULE_COLUMNS + " WHERE id = ? FOR UPDATE", (rs, row) -> rule(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.UNPROCESSABLE_CONTENT, "VOUCHER_INVALID", "Voucher does not exist"));
        return check(voucher, subtotal);
    }

    private Voucher check(Voucher voucher, BigDecimal subtotal) {
        if (!voucher.usable()) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "VOUCHER_NOT_ACTIVE",
                "Voucher is not active");
        if (subtotal.compareTo(voucher.minOrderAmount()) < 0) throw error(HttpStatus.UNPROCESSABLE_CONTENT,
                "VOUCHER_MIN_ORDER_NOT_MET", "Order subtotal is below the voucher minimum");
        if (usedCount(voucher.id()) >= voucher.usageLimit()) throw error(HttpStatus.CONFLICT,
                "VOUCHER_USAGE_LIMIT_REACHED", "Voucher usage limit reached");
        return voucher;
    }

    private int usedCount(long voucherId) {
        Integer used = jdbc.queryForObject("""
                SELECT count(*) FROM voucher_usages WHERE voucher_id = ? AND status IN ('RESERVED','CONSUMED')
                """, Integer.class, voucherId);
        return used == null ? 0 : used;
    }

    private static Voucher rule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Voucher(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7), rs.getBoolean(8));
    }

    // ---- Manager maintenance ----

    public PageResponse<VoucherView> list(String search, int page, int size) {
        String filter = search == null || search.isBlank() ? "" : " WHERE v.code LIKE ?";
        List<Object> arguments = new ArrayList<>();
        if (!filter.isEmpty()) arguments.add("%" + search.trim().toUpperCase(Locale.ROOT) + "%");
        Long total = jdbc.queryForObject("SELECT count(*) FROM vouchers v" + filter, Long.class, arguments.toArray());
        arguments.add(size);
        arguments.add((long) page * size);
        List<VoucherView> items = jdbc.query(VIEW_COLUMNS + filter + " ORDER BY v.created_at DESC, v.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> view(rs), arguments.toArray());
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    @Transactional
    public VoucherView create(long actorId, VoucherRequest request) {
        validate(request);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        requireUniqueCode(code, null);
        long id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO vouchers(code,discount_type,discount_value,max_discount,min_order_amount,usage_limit,
                                         valid_from,valid_until,status)
                    VALUES (?,?,?,?,?,?,?,?,?) RETURNING id
                    """, Long.class, code, request.discountType(), request.discountValue(), request.maxDiscount(),
                    request.minOrderAmount(), request.usageLimit(), request.validFrom(), request.validUntil(),
                    request.status());
        } catch (DuplicateKeyException exception) {
            throw error(HttpStatus.CONFLICT, "VOUCHER_CODE_EXISTS", "Voucher code already exists");
        }
        audit(actorId, "VOUCHER_CREATED", id, code);
        return get(id);
    }

    @Transactional
    public VoucherView update(long actorId, long id, VoucherRequest request) {
        validate(request);
        lock(id);
        if (request.usageLimit() < usedCount(id)) throw error(HttpStatus.CONFLICT, "VOUCHER_LIMIT_BELOW_USAGE",
                "Usage limit cannot be lower than the number of times the voucher was used");
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        requireUniqueCode(code, id);
        try {
            jdbc.update("""
                    UPDATE vouchers SET code=?,discount_type=?,discount_value=?,max_discount=?,min_order_amount=?,
                                        usage_limit=?,valid_from=?,valid_until=?,status=?
                    WHERE id=?
                    """, code, request.discountType(), request.discountValue(), request.maxDiscount(),
                    request.minOrderAmount(), request.usageLimit(), request.validFrom(), request.validUntil(),
                    request.status(), id);
        } catch (DuplicateKeyException exception) {
            throw error(HttpStatus.CONFLICT, "VOUCHER_CODE_EXISTS", "Voucher code already exists");
        }
        audit(actorId, "VOUCHER_UPDATED", id, code);
        return get(id);
    }

    /** Vouchers that were ever attached to an order are kept for history; set them INACTIVE instead. */
    @Transactional
    public void delete(long actorId, long id) {
        lock(id);
        Boolean used = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM orders WHERE voucher_id = ?)
                    OR EXISTS (SELECT 1 FROM voucher_usages WHERE voucher_id = ?)
                """, Boolean.class, id, id);
        if (Boolean.TRUE.equals(used)) throw error(HttpStatus.CONFLICT, "VOUCHER_IN_USE",
                "Voucher was used by an order; set it INACTIVE instead");
        String code = jdbc.queryForObject("DELETE FROM vouchers WHERE id = ? RETURNING code", String.class, id);
        audit(actorId, "VOUCHER_DELETED", id, code);
    }

    private VoucherView get(long id) {
        return jdbc.query(VIEW_COLUMNS + " WHERE v.id = ?", (rs, row) -> view(rs), id).stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "VOUCHER_NOT_FOUND", "Voucher not found"));
    }

    /** Checked first so a duplicate does not abort the transaction; the unique index still guards races. */
    private void requireUniqueCode(String code, Long exceptId) {
        Boolean taken = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM vouchers WHERE upper(code) = ? AND id IS DISTINCT FROM ?)",
                Boolean.class, code, exceptId);
        if (Boolean.TRUE.equals(taken))
            throw error(HttpStatus.CONFLICT, "VOUCHER_CODE_EXISTS", "Voucher code already exists");
    }

    private void lock(long id) {
        if (jdbc.queryForList("SELECT id FROM vouchers WHERE id = ? FOR UPDATE", Long.class, id).isEmpty())
            throw error(HttpStatus.NOT_FOUND, "VOUCHER_NOT_FOUND", "Voucher not found");
    }

    private static void validate(VoucherRequest request) {
        if ("PERCENT".equals(request.discountType())
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0)
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "VOUCHER_RULE_INVALID", "Percentage cannot exceed 100");
        if (!request.validUntil().isAfter(request.validFrom()))
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "VOUCHER_RULE_INVALID", "validUntil must be after validFrom");
    }

    private void audit(long actorId, String action, long id, String code) {
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                VALUES (?,?,'VOUCHER',?,jsonb_build_object('code',?::text))
                """, actorId, action, Long.toString(id), code);
    }

    private static VoucherView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VoucherView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7), rs.getInt(8),
                rs.getObject(9, OffsetDateTime.class), rs.getObject(10, OffsetDateTime.class), rs.getString(11),
                rs.getObject(12, OffsetDateTime.class));
    }

    public record VoucherView(long id, String code, String discountType, BigDecimal discountValue,
                              BigDecimal maxDiscount, BigDecimal minOrderAmount, int usageLimit, int usedCount,
                              OffsetDateTime validFrom, OffsetDateTime validUntil, String status,
                              OffsetDateTime createdAt) {}

    public record VoucherRequest(
            @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{3,60}") String code,
            @NotNull @Pattern(regexp = "FIXED|PERCENT") String discountType,
            @NotNull @Positive BigDecimal discountValue,
            @Positive BigDecimal maxDiscount,
            @NotNull @PositiveOrZero BigDecimal minOrderAmount,
            @NotNull @Min(1) Integer usageLimit,
            @NotNull OffsetDateTime validFrom,
            @NotNull OffsetDateTime validUntil,
            @NotNull @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE") String status) {}
}
