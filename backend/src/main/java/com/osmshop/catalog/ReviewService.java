package com.osmshop.catalog;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import com.osmshop.catalog.ReviewDtos.ManagerReview;
import com.osmshop.catalog.ReviewDtos.ModerateRequest;
import com.osmshop.catalog.ReviewDtos.MyReview;
import com.osmshop.catalog.ReviewDtos.PublicReview;
import com.osmshop.catalog.ReviewDtos.ReviewRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verified product reviews. A customer reviews one order item of their own DELIVERED order (one review per
 * item, editable afterwards). Only VISIBLE reviews are public and count toward the product rating.
 * The Manager can hide, restore and publicly answer a review; every moderation step is audited.
 */
@Service
public class ReviewService {
    private static final String MY_VIEW = """
            SELECT pr.id, pr.order_item_id, oi.product_id, pr.rating, pr.comment, pr.status, pr.manager_reply,
                   pr.created_at, pr.updated_at
            FROM product_reviews pr JOIN order_items oi ON oi.id = pr.order_item_id
            """;
    private static final String MANAGER_FROM = """
            FROM product_reviews pr
            JOIN order_items oi ON oi.id = pr.order_item_id
            JOIN orders o ON o.id = oi.order_id
            JOIN products p ON p.id = oi.product_id
            JOIN users u ON u.id = pr.customer_id
            """;

    private final JdbcTemplate jdbc;
    private final CatalogAudit audit;

    public ReviewService(JdbcTemplate jdbc, CatalogAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ---- Customer -------------------------------------------------------------------------------------------

    public MyReview getOwn(long customerId, long orderItemId) {
        requireOwnItem(customerId, orderItemId, false);
        return findOwn(orderItemId).orElseThrow(ReviewService::reviewNotFound);
    }

    @Transactional
    public MyReview create(long customerId, long orderItemId, ReviewRequest request) {
        String orderStatus = requireOwnItem(customerId, orderItemId, true);
        if (!"DELIVERED".equals(orderStatus)) throw notAllowed();
        if (findOwn(orderItemId).isPresent()) throw alreadyReviewed();
        try {
            jdbc.update("""
                    INSERT INTO product_reviews(order_item_id, customer_id, rating, comment, status)
                    VALUES (?, ?, ?, ?, 'VISIBLE')
                    """, orderItemId, customerId, request.rating(), blankToNull(request.comment()));
        } catch (DuplicateKeyException exception) {
            throw alreadyReviewed();
        }
        return findOwn(orderItemId).orElseThrow(ReviewService::reviewNotFound);
    }

    /** Editing keeps the moderation status: a hidden review stays hidden. */
    @Transactional
    public MyReview update(long customerId, long orderItemId, ReviewRequest request) {
        requireOwnItem(customerId, orderItemId, true);
        int changed = jdbc.update("""
                UPDATE product_reviews SET rating = ?, comment = ?, updated_at = now() WHERE order_item_id = ?
                """, request.rating(), blankToNull(request.comment()), orderItemId);
        if (changed == 0) throw reviewNotFound();
        return findOwn(orderItemId).orElseThrow(ReviewService::reviewNotFound);
    }

    // ---- Public ---------------------------------------------------------------------------------------------

    /** Same visibility rule as the product detail: an unpublished product has no public reviews. */
    public PageResponse<PublicReview> productReviews(long productId, int page, int size) {
        Boolean visible = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM products p JOIN categories c ON c.id = p.category_id
                               WHERE p.id = ? AND p.status = 'ACTIVE' AND c.status = 'ACTIVE')
                """, Boolean.class, productId);
        if (!Boolean.TRUE.equals(visible)) {
            throw new AuthApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm");
        }
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM product_reviews pr JOIN order_items oi ON oi.id = pr.order_item_id
                WHERE oi.product_id = ? AND pr.status = 'VISIBLE'
                """, Long.class, productId);
        List<PublicReview> items = jdbc.query("""
                SELECT pr.id, u.full_name, pr.rating, pr.comment, pr.manager_reply, pr.replied_at, pr.created_at,
                       pr.updated_at > pr.created_at + interval '1 second' AS edited
                FROM product_reviews pr
                JOIN order_items oi ON oi.id = pr.order_item_id
                JOIN users u ON u.id = pr.customer_id
                WHERE oi.product_id = ? AND pr.status = 'VISIBLE'
                ORDER BY pr.created_at DESC, pr.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new PublicReview(rs.getLong("id"), shortName(rs.getString("full_name")),
                        rs.getInt("rating"), rs.getString("comment"), rs.getString("manager_reply"),
                        rs.getObject("replied_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getBoolean("edited")),
                productId, size, (long) page * size);
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    // ---- Manager --------------------------------------------------------------------------------------------

    /** q matches product name, SKU, order code, customer name/email or the comment (wildcards are literal). */
    public PageResponse<ManagerReview> managerList(String status, Integer rating, String q, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE true");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND pr.status = ?");
            args.add(status);
        }
        if (rating != null) {
            where.append(" AND pr.rating = ?");
            args.add(rating);
        }
        String term = q == null ? "" : q.trim();
        if (!term.isEmpty()) {
            where.append("""
                     AND (p.name ILIKE ? ESCAPE '\\' OR p.sku ILIKE ? ESCAPE '\\' OR o.order_code ILIKE ? ESCAPE '\\'
                          OR u.full_name ILIKE ? ESCAPE '\\' OR u.email ILIKE ? ESCAPE '\\'
                          OR pr.comment ILIKE ? ESCAPE '\\')
                    """);
            String like = "%" + escapeLike(term) + "%";
            for (int i = 0; i < 6; i++) args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT count(*) " + MANAGER_FROM + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<ManagerReview> items = jdbc.query(managerSelect() + where
                + " ORDER BY pr.created_at DESC, pr.id DESC LIMIT ? OFFSET ?", ReviewService::managerView,
                pageArgs.toArray());
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    /** updated_at is left alone: it tracks the customer's own edits (shown as "đã chỉnh sửa"). */
    @Transactional
    public ManagerReview moderate(long actorId, long id, ModerateRequest request) {
        if (request.status() == null && request.reply() == null) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Hãy gửi trạng thái mới hoặc nội dung phản hồi.");
        }
        String previous = jdbc.queryForList("SELECT status FROM product_reviews WHERE id = ? FOR UPDATE",
                String.class, id).stream().findFirst().orElseThrow(ReviewService::reviewNotFound);
        if (request.status() != null && !request.status().equals(previous)) {
            jdbc.update("UPDATE product_reviews SET status = ?, moderated_by = ? WHERE id = ?",
                    request.status(), actorId, id);
            audit.record(actorId, "HIDDEN".equals(request.status()) ? "REVIEW_HIDDEN" : "REVIEW_RESTORED",
                    "REVIEW", id, "from", previous, "to", request.status());
        }
        if (request.reply() != null) {
            String reply = blankToNull(request.reply());
            jdbc.update("""
                    UPDATE product_reviews SET manager_reply = ?, replied_by = ?, replied_at = ? WHERE id = ?
                    """, reply, reply == null ? null : actorId, reply == null ? null : OffsetDateTime.now(), id);
            audit.record(actorId, reply == null ? "REVIEW_REPLY_REMOVED" : "REVIEW_REPLIED", "REVIEW", id,
                    "reply", reply);
        }
        return jdbc.query(managerSelect() + " WHERE pr.id = ?", ReviewService::managerView, id)
                .stream().findFirst().orElseThrow(ReviewService::reviewNotFound);
    }

    // ---- Helpers --------------------------------------------------------------------------------------------

    /**
     * Returns the order status of the customer's own order item. Someone else's item is reported as not found,
     * so item ids cannot be probed. lock=true serialises concurrent submits for the same item.
     */
    private String requireOwnItem(long customerId, long orderItemId, boolean lock) {
        return jdbc.queryForList("""
                SELECT o.status FROM order_items oi JOIN orders o ON o.id = oi.order_id
                WHERE oi.id = ? AND o.customer_id = ?
                """ + (lock ? " FOR UPDATE OF oi" : ""), String.class, orderItemId, customerId)
                .stream().findFirst().orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND,
                        "ORDER_ITEM_NOT_FOUND", "Không tìm thấy sản phẩm trong đơn hàng"));
    }

    private Optional<MyReview> findOwn(long orderItemId) {
        return jdbc.query(MY_VIEW + " WHERE pr.order_item_id = ?", (rs, row) -> new MyReview(rs.getLong("id"),
                rs.getLong("order_item_id"), rs.getLong("product_id"), rs.getInt("rating"), rs.getString("comment"),
                rs.getString("status"), rs.getString("manager_reply"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)),
                orderItemId).stream().findFirst();
    }

    private static String managerSelect() {
        return """
                SELECT pr.id, oi.product_id, p.name AS product_name, p.sku, o.id AS order_id, o.order_code,
                       u.full_name, u.email, pr.rating, pr.comment, pr.status, pr.manager_reply, pr.replied_at,
                       pr.created_at, pr.updated_at
                """ + MANAGER_FROM;
    }

    private static ManagerReview managerView(ResultSet rs, int row) throws SQLException {
        return new ManagerReview(rs.getLong("id"), rs.getLong("product_id"), rs.getString("product_name"),
                rs.getString("sku"), rs.getLong("order_id"), rs.getString("order_code"), rs.getString("full_name"),
                rs.getString("email"), rs.getInt("rating"), rs.getString("comment"), rs.getString("status"),
                rs.getString("manager_reply"), rs.getObject("replied_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    /** "Phạm Văn Đức" becomes "Phạm Đ."; a single word is shown as is. */
    static String shortName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Khách hàng";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        String last = parts[parts.length - 1];
        return parts[0] + " " + last.substring(0, last.offsetByCodePoints(0, 1)) + ".";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static AuthApiException reviewNotFound() {
        return new AuthApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Không tìm thấy đánh giá");
    }

    /** MSG-034 */
    private static AuthApiException notAllowed() {
        return new AuthApiException(HttpStatus.UNPROCESSABLE_CONTENT, "REVIEW_NOT_ALLOWED",
                "Bạn chỉ có thể đánh giá sản phẩm trong đơn hàng đã giao.");
    }

    /** MSG-035 */
    private static AuthApiException alreadyReviewed() {
        return new AuthApiException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS",
                "Bạn đã đánh giá sản phẩm này cho đơn hàng này. Hãy sửa đánh giá hiện có.");
    }
}
